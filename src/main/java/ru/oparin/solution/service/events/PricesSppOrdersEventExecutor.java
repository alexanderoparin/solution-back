package ru.oparin.solution.service.events;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.oparin.solution.model.WbApiEvent;
import ru.oparin.solution.service.CabinetService;
import ru.oparin.solution.service.events.payload.MainStepPayload;
import ru.oparin.solution.service.sync.ProductPricesSyncService;

@Component("pricesSppOrdersEventExecutor")
@RequiredArgsConstructor
public class PricesSppOrdersEventExecutor implements WbApiEventExecutor {

    private final WbApiEventService eventService;
    private final CabinetService cabinetService;
    private final ProductPricesSyncService productPricesSyncService;

    @Override
    public WbApiEventExecutionResult execute(WbApiEvent event) {
        MainStepPayload payload = eventService.readPayload(event, MainStepPayload.class);
        var cabinet = cabinetService.findByIdWithUserOrThrow(event.getCabinet().getId());
        if (cabinet.getApiKey() == null || cabinet.getApiKey().isBlank()) {
            return WbApiEventExecutionResult.finalError("У кабинета отсутствует API ключ");
        }
        try {
            productPricesSyncService.updateSppFromOrders(cabinet, cabinet.getApiKey());
            eventService.tryFinalizeMain(cabinet.getId(), event.getId());
            return WbApiEventExecutionResult.completedSuccessfully();
        } catch (Exception e) {
            return WbApiEventExecutionResult.retryableError(e.getMessage());
        }
    }
}
