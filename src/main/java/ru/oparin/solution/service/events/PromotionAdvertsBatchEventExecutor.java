package ru.oparin.solution.service.events;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.oparin.solution.exception.WbApiUnauthorizedScopeException;
import ru.oparin.solution.model.WbApiEvent;
import ru.oparin.solution.service.CabinetService;
import ru.oparin.solution.service.events.payload.MainStepPayload;
import ru.oparin.solution.service.events.payload.PromotionAdvertsBatchPayload;
import ru.oparin.solution.service.sync.PromotionCampaignSyncService;

@Component("promotionAdvertsBatchEventExecutor")
@RequiredArgsConstructor
public class PromotionAdvertsBatchEventExecutor implements WbApiEventExecutor {

    private final WbApiEventService eventService;
    private final CabinetService cabinetService;
    private final PromotionCampaignSyncService promotionCampaignSyncService;

    @Override
    public WbApiEventExecutionResult execute(WbApiEvent event) {
        PromotionAdvertsBatchPayload payload = eventService.readPayload(event, PromotionAdvertsBatchPayload.class);
        var cabinet = cabinetService.findByIdWithUserOrThrow(event.getCabinet().getId());
        if (cabinet.getApiKey() == null || cabinet.getApiKey().isBlank()) {
            return WbApiEventExecutionResult.finalError("У кабинета отсутствует API ключ");
        }
        try {
            promotionCampaignSyncService.loadAndSaveAdvertsBatch(cabinet, cabinet.getApiKey(), payload.campaignIds());
            MainStepPayload mainPayload = MainStepPayload.builder()
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
            return WbApiEventExecutionResult.retryableError(e.getMessage());
        }
    }
}
