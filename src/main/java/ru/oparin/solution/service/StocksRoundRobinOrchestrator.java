package ru.oparin.solution.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.oparin.solution.exception.WbApiUnauthorizedScopeException;
import ru.oparin.solution.model.Cabinet;
import ru.oparin.solution.model.CabinetUpdateErrorScope;
import ru.oparin.solution.model.ProductCard;
import ru.oparin.solution.service.wb.WbApiCategory;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Второй этап полного обновления: синхронизация остатков по кабинетам в round-robin режиме.
 * Запускается строго после завершения main-пайплайна для всех кабинетов.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StocksRoundRobinOrchestrator {

    private final ProductStocksService productStocksService;
    private final ProductCardService productCardService;
    private final CabinetService cabinetService;
    private final CabinetScopeStatusService cabinetScopeStatusService;
    private final CabinetUpdateErrorService cabinetUpdateErrorService;

    public void runStocksRoundRobin(List<Cabinet> cabinets, String scopeLabel) {
        List<CabinetState> states = buildStates(cabinets);
        log.info("Запуск round-robin обновления остатков по {}. Кабинетов к обработке: {}", scopeLabel, states.size());

        if (states.isEmpty()) {
            log.info("Round-robin обновление остатков пропущено: нет кабинетов с товарами и ключом.");
            return;
        }

        Deque<CabinetState> queue = new ArrayDeque<>(states);

        while (!queue.isEmpty()) {
            int iterationSize = queue.size();

            for (int i = 0; i < iterationSize; i++) {
                CabinetState state = queue.pollFirst();
                if (state == null) {
                    continue;
                }
                processOneNmId(state);
                if (state.hasNext()) {
                    queue.offerLast(state);
                } else {
                    completeCabinetStocksUpdate(state.cabinet());
                }
            }
        }

        log.info("Round-robin обновление остатков по {} завершено.", scopeLabel);
    }

    private List<CabinetState> buildStates(List<Cabinet> cabinets) {
        return cabinets.stream()
                .filter(Objects::nonNull)
                .filter(cabinet -> cabinet.getApiKey() != null && !cabinet.getApiKey().isBlank())
                .map(this::createCabinetState)
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(state -> state.cabinet().getLastStocksUpdateAt(),
                        Comparator.nullsFirst(Comparator.naturalOrder())))
                .toList();
    }

    private CabinetState createCabinetState(Cabinet cabinet) {
        List<Long> nmIds = productCardService.findByCabinetId(cabinet.getId()).stream()
                .map(ProductCard::getNmId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (nmIds.isEmpty()) {
            return null;
        }
        return new CabinetState(cabinet, nmIds, 0);
    }

    private void processOneNmId(CabinetState state) {
        Long nmId = state.currentNmId();
        Long cabinetId = state.cabinet().getId();
        try {
            productStocksService.getWbStocksBySizes(state.cabinet().getApiKey(), nmId, state.cabinet());
            cabinetScopeStatusService.recordSuccess(cabinetId, WbApiCategory.ANALYTICS);
        } catch (WbApiUnauthorizedScopeException e) {
            cabinetScopeStatusService.recordFailure(cabinetId, e.getCategory(), e.getMessage());
            cabinetUpdateErrorService.recordError(cabinetId, CabinetUpdateErrorScope.STOCKS, e.getMessage());
            log.warn("Round-robin остатки: кабинет {} отключен из-за недостаточных прав WB API ({})",
                    cabinetId, e.getCategory().getDisplayName());
            state.finish();
            return;
        } catch (Exception e) {
            cabinetUpdateErrorService.recordError(cabinetId, CabinetUpdateErrorScope.STOCKS,
                    "nmID " + nmId + ": " + e.getMessage());
            log.warn("Round-robin остатки: ошибка для кабинета {}, nmID {}: {}",
                    cabinetId, nmId, e.getMessage());
        }

        state.advance();
    }

    private void completeCabinetStocksUpdate(Cabinet cabinet) {
        cabinet.setLastStocksUpdateAt(LocalDateTime.now());
        cabinetService.save(cabinet);
    }

    private static final class CabinetState {
        private final Cabinet cabinet;
        private final List<Long> nmIds;
        private int index;

        private CabinetState(Cabinet cabinet, List<Long> nmIds, int index) {
            this.cabinet = cabinet;
            this.nmIds = nmIds;
            this.index = index;
        }

        private Cabinet cabinet() {
            return cabinet;
        }

        private boolean hasNext() {
            return index < nmIds.size();
        }

        private Long currentNmId() {
            return nmIds.get(index);
        }

        private void advance() {
            index++;
        }

        private void finish() {
            index = nmIds.size();
        }
    }
}
