package ru.oparin.solution.service.events;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.oparin.solution.model.WbApiEvent;
import ru.oparin.solution.service.CabinetService;
import ru.oparin.solution.service.sync.WbProductPricesSyncService;

/**
 * Одно событие: все батчи цен по кабинету, затем СПП из заказов (без отдельного события СПП в очереди).
 */
@Component("pricesCabinetWithSppEventExecutor")
@RequiredArgsConstructor
public class WbPricesCabinetWithSppEventExecutor implements WbApiEventExecutor {

    private final WbApiEventService eventService;
    private final CabinetService cabinetService;
    private final WbProductPricesSyncService productPricesSyncService;

    @Override
    public WbApiEventExecutionResult execute(WbApiEvent event) {
        var cabinet = cabinetService.findByIdWithUserOrThrow(event.getCabinet().getId());
        if (cabinet.getApiKey() == null || cabinet.getApiKey().isBlank()) {
            return WbApiEventExecutionResult.finalError("У кабинета отсутствует API ключ");
        }
        try {
            productPricesSyncService.loadPriceBatchesThenSppFromOrders(cabinet, cabinet.getApiKey());
            eventService.tryFinalizeMain(cabinet.getId(), event.getId());
            return WbApiEventExecutionResult.completedSuccessfully();
        } catch (Exception e) {
            return WbEventExecutionErrors.wrapDeferOrRetryable(e);
        }
    }
}
