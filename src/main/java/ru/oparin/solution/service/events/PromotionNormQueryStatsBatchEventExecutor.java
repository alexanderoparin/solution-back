package ru.oparin.solution.service.events;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.oparin.solution.exception.WbApiUnauthorizedScopeException;
import ru.oparin.solution.model.WbApiEvent;
import ru.oparin.solution.service.CabinetService;
import ru.oparin.solution.service.events.payload.PromotionNormQueryStatsBatchPayload;
import ru.oparin.solution.service.sync.PromotionCampaignSyncService;

@Component("promotionNormQueryStatsBatchEventExecutor")
@RequiredArgsConstructor
public class PromotionNormQueryStatsBatchEventExecutor implements WbApiEventExecutor {

    private final WbApiEventService eventService;
    private final CabinetService cabinetService;
    private final PromotionCampaignSyncService promotionCampaignSyncService;

    @Override
    public WbApiEventExecutionResult execute(WbApiEvent event) {
        PromotionNormQueryStatsBatchPayload payload =
                eventService.readPayload(event, PromotionNormQueryStatsBatchPayload.class);
        var cabinet = cabinetService.findByIdWithUserOrThrow(event.getCabinet().getId());
        if (cabinet.getApiKey() == null || cabinet.getApiKey().isBlank()) {
            return WbApiEventExecutionResult.finalError("У кабинета отсутствует API ключ");
        }
        try {
            promotionCampaignSyncService.loadAndSaveNormQueryStatsBatch(
                    cabinet.getApiKey(),
                    cabinet.getId(),
                    payload.campaignIds(),
                    payload.dateFrom(),
                    payload.dateTo()
            );
            if (!eventService.hasOtherActivePromotionNormQueryStatsBatches(
                    cabinet.getId(),
                    event.getId(),
                    payload.dateFrom(),
                    payload.dateTo()
            )) {
                eventService.tryFinalizeMain(cabinet.getId(), event.getId());
            }
            return WbApiEventExecutionResult.completedSuccessfully();
        } catch (WbApiUnauthorizedScopeException e) {
            return WbApiEventExecutionResult.finalError(e.getMessage());
        } catch (Exception e) {
            return WbEventExecutionErrors.wrapDeferOrRetryable(e);
        }
    }
}
