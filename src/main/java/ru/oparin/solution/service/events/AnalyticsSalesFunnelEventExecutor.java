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
        long eventCabinetId = event.getCabinet().getId();

        ProductCard card = productCardService.findByNmId(payload.nmId()).orElse(null);
        if (card == null) {
            return WbApiEventExecutionResult.finalError("Карточка не найдена для nmID=" + payload.nmId());
        }

        var cardCabinet = cabinetService.findByIdWithUserOrThrow(card.getCabinet().getId());
        if (cardCabinet.getApiKey() == null || cardCabinet.getApiKey().isBlank()) {
            return WbApiEventExecutionResult.finalError("У кабинета карточки отсутствует API ключ");
        }

        try {
            analyticsLoadService.loadAnalyticsForCard(
                    card, cardCabinet.getApiKey(), payload.dateFrom(), payload.dateTo());
            eventService.tryFinalizeMain(eventCabinetId, event.getId());
            return WbApiEventExecutionResult.completedSuccessfully();
        } catch (Exception e) {
            return WbApiEventExecutionResult.retryableError(e.getMessage());
        }
    }
}
