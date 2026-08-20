package ru.oparin.solution.service.events;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import ru.oparin.solution.dto.wb.WbSellerWarehouseResponse;
import ru.oparin.solution.exception.WbApiUnauthorizedScopeException;
import ru.oparin.solution.model.Cabinet;
import ru.oparin.solution.model.WbApiEvent;
import ru.oparin.solution.model.WbSellerWarehouse;
import ru.oparin.solution.service.CabinetScopeStatusService;
import ru.oparin.solution.service.CabinetService;
import ru.oparin.solution.service.WbSellerWarehouseService;
import ru.oparin.solution.service.events.payload.WbMainStepPayload;
import ru.oparin.solution.service.wb.WbApiCategory;
import ru.oparin.solution.service.wb.WbFbsApiClient;

import java.util.List;

/**
 * Синхронизация складов продавца кабинета и постановка события остатков FBS.
 */
@Component("fbsWbWarehousesSyncCabinetEventExecutor")
@RequiredArgsConstructor
@Slf4j
public class WbFbsWarehousesSyncCabinetEventExecutor implements WbApiEventExecutor {

    private final WbApiEventService eventService;
    private final CabinetService cabinetService;
    private final WbFbsApiClient fbsApiClient;
    private final WbSellerWarehouseService sellerWarehouseService;
    private final CabinetScopeStatusService cabinetScopeStatusService;

    @Override
    public WbApiEventExecutionResult execute(WbApiEvent event) {
        eventService.readPayload(event, WbMainStepPayload.class);
        Cabinet cabinet = cabinetService.findByIdWithUserOrThrow(event.getCabinet().getId());
        long cabinetId = cabinet.getId();
        if (cabinet.getApiKey() == null || cabinet.getApiKey().isBlank()) {
            return WbApiEventExecutionResult.finalError("У кабинета отсутствует API ключ");
        }
        try {
            List<WbSellerWarehouseResponse> warehouses = fbsApiClient.getWbSellerWarehouses(cabinet.getApiKey());
            List<WbSellerWarehouse> saved = sellerWarehouseService.saveOrUpdateForCabinet(cabinet, warehouses);
            cabinetScopeStatusService.recordSuccess(cabinetId, WbApiCategory.MARKETPLACE);
            if (!saved.isEmpty()) {
                eventService.enqueueFbsStocksCabinetEvent(cabinetId, event.getTriggerSource());
            }
            return WbApiEventExecutionResult.completedSuccessfully();
        } catch (WbApiUnauthorizedScopeException e) {
            cabinetScopeStatusService.recordFailure(cabinetId, e.getCategory(), e.getMessage());
            log.warn("Не удалось обновить склады продавца кабинета {}, нет доступа к категории WB API: {}",
                    cabinetId, e.getCategory().getDisplayName());
            return WbApiEventExecutionResult.completedSuccessfully();
        } catch (HttpClientErrorException ex) {
            log.warn("Не удалось обновить склады продавца кабинета {}, код ошибки {}",
                    cabinetId, ex.getStatusCode());
            return WbApiEventExecutionResult.completedSuccessfully();
        } catch (Exception e) {
            return WbEventExecutionErrors.wrapDeferOrRetryable(e);
        }
    }
}
