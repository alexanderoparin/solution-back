package ru.oparin.solution.service.events;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.oparin.solution.model.WbApiEvent;
import ru.oparin.solution.service.CabinetService;
import ru.oparin.solution.service.events.payload.PricesProductsBatchPayload;
import ru.oparin.solution.service.sync.ProductPricesSyncService;

import java.time.LocalDate;

@Component("pricesProductsBatchEventExecutor")
@RequiredArgsConstructor
public class PricesProductsBatchEventExecutor implements WbApiEventExecutor {

    private final WbApiEventService eventService;
    private final CabinetService cabinetService;
    private final ProductPricesSyncService productPricesSyncService;

    @Override
    public WbApiEventExecutionResult execute(WbApiEvent event) {
        PricesProductsBatchPayload payload = eventService.readPayload(event, PricesProductsBatchPayload.class);
        var cabinet = cabinetService.findByIdWithUserOrThrow(event.getCabinet().getId());
        if (cabinet.getApiKey() == null || cabinet.getApiKey().isBlank()) {
            return WbApiEventExecutionResult.finalError("У кабинета отсутствует API ключ");
        }
        try {
            LocalDate date = LocalDate.now().minusDays(1);
            productPricesSyncService.loadPricesBatch(cabinet, cabinet.getApiKey(), date, payload.nmIds());
            eventService.tryFinalizeMain(cabinet.getId(), payload.includeStocks(), event.getTriggerSource(), event.getId());
            return WbApiEventExecutionResult.completedSuccessfully();
        } catch (Exception e) {
            return WbApiEventExecutionResult.retryableError(e.getMessage());
        }
    }
}
