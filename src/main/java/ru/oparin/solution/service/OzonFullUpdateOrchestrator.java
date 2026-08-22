package ru.oparin.solution.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.oparin.solution.model.Cabinet;
import ru.oparin.solution.model.Role;
import ru.oparin.solution.service.events.OzonApiEventService;

import java.util.Comparator;
import java.util.List;

/**
 * Полное read-only обновление Ozon-кабинетов: каталог → цены → остатки (при includeStocks).
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class OzonFullUpdateOrchestrator {

    private final CabinetService cabinetService;
    private final OzonApiEventService ozonApiEventService;

    public void runFullUpdate() {
        runFullUpdate(false);
    }

    public void runFullUpdate(boolean includeStocks) {
        List<Cabinet> cabinets = cabinetService.findOzonCabinetsWithApiKeyAndUser(Role.USER);
        cabinets = cabinets.stream()
                .sorted(Comparator.comparing(Cabinet::getLastDataUpdateAt, Comparator.nullsFirst(Comparator.naturalOrder())))
                .toList();
        log.info("Ozon full update: кабинетов с ключами {}", cabinets.size());
        if (cabinets.isEmpty()) {
            return;
        }
        cabinets.forEach(cabinet -> ozonApiEventService.enqueueInitialProductListEvent(
                cabinet.getId(),
                includeStocks,
                "SCHEDULED"
        ));
        log.info("Созданы PRODUCT_LIST события для {} Ozon-кабинетов", cabinets.size());
    }
}
