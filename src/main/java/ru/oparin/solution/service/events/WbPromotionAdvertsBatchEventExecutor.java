package ru.oparin.solution.service.events;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.oparin.solution.exception.WbApiUnauthorizedScopeException;
import ru.oparin.solution.model.WbApiEvent;
import ru.oparin.solution.service.CabinetService;
import ru.oparin.solution.service.events.payload.WbMainStepPayload;
import ru.oparin.solution.service.events.payload.WbPromotionAdvertsBatchPayload;
import ru.oparin.solution.service.sync.WbPromotionCampaignSyncService;

@Component("promotionAdvertsBatchEventExecutor")
@RequiredArgsConstructor
public class WbPromotionAdvertsBatchEventExecutor implements WbApiEventExecutor {

    private final WbApiEventService eventService;
    private final CabinetService cabinetService;
    private final WbPromotionCampaignSyncService promotionCampaignSyncService;

    @Override
    public WbApiEventExecutionResult execute(WbApiEvent event) {
        WbPromotionAdvertsBatchPayload payload = eventService.readPayload(event, WbPromotionAdvertsBatchPayload.class);
        var cabinet = cabinetService.findByIdWithUserOrThrow(event.getCabinet().getId());
        if (cabinet.getApiKey() == null || cabinet.getApiKey().isBlank()) {
            return WbApiEventExecutionResult.finalError("У кабинета отсутствует API ключ");
        }
        try {
            promotionCampaignSyncService.loadAndSaveAdvertsBatch(cabinet, cabinet.getApiKey(), payload.campaignIds());
            WbMainStepPayload mainPayload = WbMainStepPayload.builder()
                    .dateFrom(payload.dateFrom())
                    .dateTo(payload.dateTo())
                    .includeStocks(payload.includeStocks())
                    .build();
            eventService.schedulePromotionStatsAfterAdvertsIfReady(
                    cabinet.getId(),
                    mainPayload,
                    event.getTriggerSource(),
                    event.getId()
            );
            return WbApiEventExecutionResult.completedSuccessfully();
        } catch (WbApiUnauthorizedScopeException e) {
            return WbApiEventExecutionResult.finalError(e.getMessage());
        } catch (Exception e) {
            return WbEventExecutionErrors.wrapDeferOrRetryable(e);
        }
    }
}
