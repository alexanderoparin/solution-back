package ru.oparin.solution.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.oparin.solution.model.Cabinet;
import ru.oparin.solution.model.Role;
import ru.oparin.solution.service.events.WbApiEventService;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

/**
 * Единая точка входа для полного обновления по всем кабинетам (как ночной шедулер).
 * Только оркестрирует: создает события CONTENT в очереди WB API.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class FullUpdateOrchestrator {

    private final CabinetService cabinetService;
    private final WbApiEventService wbApiEventService;

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

        cabinets.forEach(cabinet -> wbApiEventService.enqueueInitialContentEvent(
                cabinet.getId(),
                from,
                to,
                includeStocks,
                "SCHEDULED"
        ));
        log.info("Созданы CONTENT события для {} кабинетов (includeStocks={}). Дальнейшее выполнение идет через event dispatcher.",
                cabinets.size(), includeStocks);
    }

}
