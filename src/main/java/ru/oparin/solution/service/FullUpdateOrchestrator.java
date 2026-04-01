package ru.oparin.solution.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import ru.oparin.solution.model.Cabinet;
import ru.oparin.solution.model.Role;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * Единая точка входа для полного обновления по всем кабинетам (как ночной шедулер).
 * Только оркестрирует: для каждого кабинета вызывает свой сервис в своей транзакции.
 * Не выполняет бизнес-логику и не открывает транзакции сам.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class FullUpdateOrchestrator {

    private final CabinetService cabinetService;
    private final ProductCardAnalyticsService productCardAnalyticsService;
    private final PromotionCalendarService promotionCalendarService;
    private final StocksRoundRobinOrchestrator stocksRoundRobinOrchestrator;
    @Qualifier("cabinetUpdateExecutor")
    private final Executor cabinetUpdateExecutor;

    /**
     * Полное обновление по всем кабинетам с API-ключом: для каждого кабинета — обновление аналитики (своя транзакция),
     * затем синхронизация акций календаря (своя транзакция). Кабинеты обрабатываются параллельно.
     */
    public void runFullUpdate() {
        runFullUpdate(false);
    }

    public void runFullUpdate(boolean includeStocks) {
        List<Cabinet> cabinets = cabinetService.findCabinetsWithApiKeyAndUser(Role.SELLER);
        runFullUpdateForCabinets(cabinets, "всем кабинетам", includeStocks);
    }

    public void runFullUpdateForManager(Long managerId, boolean includeStocks) {
        List<Cabinet> cabinets = cabinetService.findCabinetsWithApiKeyAndUserAndOwnerId(Role.SELLER, managerId);
        runFullUpdateForCabinets(cabinets, "кабинетам менеджера " + managerId, includeStocks);
    }

    private void runFullUpdateForCabinets(List<Cabinet> sourceCabinets, String scopeLabel, boolean includeStocks) {
        List<Cabinet> cabinets = sourceCabinets.stream()
                .sorted(Comparator.comparing(Cabinet::getLastDataUpdateAt, Comparator.nullsFirst(Comparator.naturalOrder())))
                .toList();
        log.info("Запуск полного обновления по {}. Найдено кабинетов с API-ключом: {}, includeStocks={}",
                scopeLabel, cabinets.size(), includeStocks);

        if (cabinets.isEmpty()) {
            log.info("Кабинетов с ключом не найдено, обновление пропущено");
            return;
        }

        LocalDate to = LocalDate.now().minusDays(1);
        LocalDate from = to.minusDays(13);
        log.info("Период для загрузки аналитики: {} - {}", from, to);

        List<CompletableFuture<Void>> futures = cabinets.stream()
                .map(cabinet -> CompletableFuture.runAsync(
                        () -> runCabinetMainUpdate(cabinet, from, to),
                        cabinetUpdateExecutor
                ))
                .toList();

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        log.info("Основное обновление завершено по {} кабинетам.", cabinets.size());
        if (includeStocks) {
            stocksRoundRobinOrchestrator.runStocksRoundRobin(cabinets, scopeLabel);
        }
        log.info("Завершено полное обновление по {} кабинетам.", cabinets.size());
    }

    private void runCabinetMainUpdate(Cabinet cabinet, LocalDate from, LocalDate to) {
        String prevName = Thread.currentThread().getName();
        try {
            Thread.currentThread().setName("full-update-cabinet-" + cabinet.getId());
            MDC.put("cabinetTag", "[cabinet:" + cabinet.getId() + "]");
            productCardAnalyticsService.updateCabinetAnalyticsInTransaction(cabinet, from, to, false);
            promotionCalendarService.syncPromotionsForCabinet(cabinet);
        } catch (Exception e) {
            log.error("Ошибка при полном обновлении кабинета (ID: {}, продавец: {}): {}",
                    cabinet.getId(), cabinet.getUser().getEmail(), e.getMessage());
        } finally {
            Thread.currentThread().setName(prevName);
            MDC.remove("cabinetTag");
        }
    }
}
