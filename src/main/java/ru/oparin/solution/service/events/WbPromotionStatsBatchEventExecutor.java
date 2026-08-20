package ru.oparin.solution.service.events;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.oparin.solution.exception.WbApiUnauthorizedScopeException;
import ru.oparin.solution.model.WbApiEvent;
import ru.oparin.solution.service.CabinetService;
import ru.oparin.solution.service.events.payload.WbPromotionStatsBatchPayload;
import ru.oparin.solution.service.sync.WbPromotionCampaignSyncService;

@Component("promotionStatsBatchEventExecutor")
@RequiredArgsConstructor
public class WbPromotionStatsBatchEventExecutor implements WbApiEventExecutor {

    private final WbApiEventService eventService;
    private final CabinetService cabinetService;
    private final WbPromotionCampaignSyncService promotionCampaignSyncService;

    @Override
    public WbApiEventExecutionResult execute(WbApiEvent event) {
        WbPromotionStatsBatchPayload payload = eventService.readPayload(event, WbPromotionStatsBatchPayload.class);
        var cabinet = cabinetService.findByIdWithUserOrThrow(event.getCabinet().getId());
        if (cabinet.getApiKey() == null || cabinet.getApiKey().isBlank()) {
            return WbApiEventExecutionResult.finalError("У кабинета отсутствует API ключ");
        }
        try {
            promotionCampaignSyncService.loadAndSaveStatisticsBatch(
                    cabinet.getUser(),
                    cabinet.getApiKey(),
                    payload.campaignIds(),
                    payload.dateFrom(),
                    payload.dateTo()
            );
            if (!eventService.hasOtherActivePromotionStatsBatches(
                    cabinet.getId(),
                    event.getId(),
                    payload.dateFrom(),
                    payload.dateTo()
            )) {
                eventService.schedulePromotionNormQueryStatsIfReady(
                        cabinet.getId(),
                        payload.dateFrom(),
                        payload.dateTo(),
                        payload.includeStocks(),
                        event.getTriggerSource(),
                        event.getId()
                );
            }
            return WbApiEventExecutionResult.completedSuccessfully();
        } catch (WbApiUnauthorizedScopeException e) {
            return WbApiEventExecutionResult.finalError(e.getMessage());
        } catch (Exception e) {
            return WbEventExecutionErrors.wrapDeferOrRetryable(e);
        }
    }
}
