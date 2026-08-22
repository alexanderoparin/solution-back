package ru.oparin.solution.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.oparin.solution.model.Cabinet;
import ru.oparin.solution.model.MarketplaceType;
import ru.oparin.solution.service.events.OzonApiEventService;
import ru.oparin.solution.service.events.WbApiEventService;

import java.time.LocalDate;

/**
 * Маршрутизация полного обновления кабинета по типу маркетплейса.
 */
@Service
@RequiredArgsConstructor
public class MarketplaceSyncOrchestrator {

    private final WbApiEventService wbApiEventService;
    private final OzonApiEventService ozonApiEventService;

    public void enqueueCabinetUpdate(
            Cabinet cabinet,
            LocalDate dateFrom,
            LocalDate dateTo,
            boolean includeStocks,
            String triggerSource
    ) {
        if (cabinet.getMarketplaceType() == MarketplaceType.OZON) {
            ozonApiEventService.enqueueInitialProductListEvent(cabinet.getId(), includeStocks, triggerSource);
            return;
        }
        wbApiEventService.enqueueInitialContentEvent(
                cabinet.getId(),
                dateFrom,
                dateTo,
                includeStocks,
                triggerSource
        );
    }
}
