package ru.oparin.solution.service.events;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import ru.oparin.solution.model.ProductCard;
import ru.oparin.solution.model.WbApiEvent;
import ru.oparin.solution.service.CabinetService;
import ru.oparin.solution.service.ProductCardService;
import ru.oparin.solution.service.events.payload.AnalyticsSalesFunnelPayload;
import ru.oparin.solution.service.sync.ProductCardAnalyticsLoadService;

@Component("analyticsSalesFunnelEventExecutor")
@RequiredArgsConstructor
@Slf4j
public class AnalyticsSalesFunnelEventExecutor implements WbApiEventExecutor {

    private final WbApiEventService eventService;
    private final ProductCardService productCardService;
    private final CabinetService cabinetService;
    private final ProductCardAnalyticsLoadService analyticsLoadService;

    @Override
    public WbApiEventExecutionResult execute(WbApiEvent event) {
        AnalyticsSalesFunnelPayload payload = eventService.readPayload(event, AnalyticsSalesFunnelPayload.class);
        var cabinet = cabinetService.findByIdWithUserOrThrow(event.getCabinet().getId());
        if (cabinet.getApiKey() == null || cabinet.getApiKey().isBlank()) {
            return WbApiEventExecutionResult.finalError("У кабинета отсутствует API ключ");
        }

        ProductCard card = productCardService.findByNmIdAndCabinetId(payload.nmId(), cabinet.getId())
                .orElse(null);
        if (card == null) {
            return WbApiEventExecutionResult.finalError("Карточка не найдена в кабинете для nmID=" + payload.nmId());
        }

        try {
            analyticsLoadService.loadAnalyticsForCard(card, cabinet.getApiKey(), payload.dateFrom(), payload.dateTo());
            eventService.tryFinalizeMain(cabinet.getId(), event.getId());
            return WbApiEventExecutionResult.completedSuccessfully();
        } catch (Exception e) {
            return WbApiEventExecutionResult.retryableError(e.getMessage());
        }
    }
}
