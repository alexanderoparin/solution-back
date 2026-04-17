package ru.oparin.solution.service.events;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import ru.oparin.solution.dto.wb.WbWarehouseResponse;
import ru.oparin.solution.exception.WbApiUnauthorizedScopeException;
import ru.oparin.solution.model.WbApiEvent;
import ru.oparin.solution.service.CabinetScopeStatusService;
import ru.oparin.solution.service.CabinetService;
import ru.oparin.solution.service.WbWarehouseService;
import ru.oparin.solution.service.events.payload.MainStepPayload;
import ru.oparin.solution.service.wb.WbApiCategory;
import ru.oparin.solution.service.wb.WbWarehousesApiClient;

import java.util.List;

@Component("warehousesSyncCabinetEventExecutor")
@RequiredArgsConstructor
@Slf4j
public class WarehousesSyncCabinetEventExecutor implements WbApiEventExecutor {

    private final WbApiEventService eventService;
    private final CabinetService cabinetService;
    private final WbWarehousesApiClient warehousesApiClient;
    private final WbWarehouseService warehouseService;
    private final CabinetScopeStatusService cabinetScopeStatusService;

    @Override
    public WbApiEventExecutionResult execute(WbApiEvent event) {
        eventService.readPayload(event, MainStepPayload.class);
        var cabinet = cabinetService.findByIdWithUserOrThrow(event.getCabinet().getId());
        long cabinetId = cabinet.getId();
        if (cabinet.getApiKey() == null || cabinet.getApiKey().isBlank()) {
            return WbApiEventExecutionResult.finalError("У кабинета отсутствует API ключ");
        }
        try {
            List<WbWarehouseResponse> warehouses = warehousesApiClient.getWbOffices(cabinet.getApiKey());
            warehouseService.saveOrUpdateWarehouses(warehouses);
            cabinetScopeStatusService.recordSuccess(cabinetId, WbApiCategory.MARKETPLACE);
            return WbApiEventExecutionResult.completedSuccessfully();
        } catch (WbApiUnauthorizedScopeException e) {
            cabinetScopeStatusService.recordFailure(cabinetId, e.getCategory(), e.getMessage());
            log.warn("Не удалось обновить склады с кабинета {}, нет доступа к категории WB API: {}", cabinetId, e.getCategory().getDisplayName());
            return WbApiEventExecutionResult.completedSuccessfully();
        } catch (HttpClientErrorException ex) {
            log.warn("Не удалось обновить склады с кабинета {}, получили код ошибки {}", cabinetId, ex.getStatusCode());
            return WbApiEventExecutionResult.completedSuccessfully();
        } catch (Exception e) {
            return WbEventExecutionErrors.wrapDeferOrRetryable(e);
        }
    }
}
