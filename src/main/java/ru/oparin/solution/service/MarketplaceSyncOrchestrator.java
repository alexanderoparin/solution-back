package ru.oparin.solution.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import ru.oparin.solution.exception.UserException;
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
        assertCabinetEligibleForSync(cabinet);
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

    private void assertCabinetEligibleForSync(Cabinet cabinet) {
        if (cabinet.getApiKey() == null || cabinet.getApiKey().isBlank()) {
            throw new UserException("У кабинета не задан API-ключ", HttpStatus.BAD_REQUEST);
        }
        if (cabinet.getMarketplaceType() == MarketplaceType.OZON
                && (cabinet.getOzonClientId() == null || cabinet.getOzonClientId().isBlank())) {
            throw new UserException("Для Ozon-кабинета не задан Client-Id", HttpStatus.BAD_REQUEST);
        }
    }
}
