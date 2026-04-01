package ru.oparin.solution.service.events;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.oparin.solution.exception.WbApiUnauthorizedScopeException;
import ru.oparin.solution.model.WbApiEvent;
import ru.oparin.solution.service.CabinetService;
import ru.oparin.solution.service.events.payload.MainStepPayload;
import ru.oparin.solution.service.sync.PromotionCampaignSyncService;

import java.util.List;

@Component("promotionCountEventExecutor")
@RequiredArgsConstructor
public class PromotionCountEventExecutor implements WbApiEventExecutor {

    private final WbApiEventService eventService;
    private final CabinetService cabinetService;
    private final PromotionCampaignSyncService promotionCampaignSyncService;

    @Override
    public WbApiEventExecutionResult execute(WbApiEvent event) {
        MainStepPayload payload = eventService.readPayload(event, MainStepPayload.class);
        var cabinet = cabinetService.findByIdWithUserOrThrow(event.getCabinet().getId());
        if (cabinet.getApiKey() == null || cabinet.getApiKey().isBlank()) {
            return WbApiEventExecutionResult.finalError("У кабинета отсутствует API ключ");
        }
        try {
            var countResponse = promotionCampaignSyncService.fetchPromotionCount(cabinet.getApiKey());
            List<Long> ids = promotionCampaignSyncService.listCampaignIdsFromCount(countResponse);
            if (ids.isEmpty()) {
                eventService.tryFinalizeMain(cabinet.getId(), payload.includeStocks(), event.getTriggerSource(), event.getId());
                return WbApiEventExecutionResult.completedSuccessfully();
            }
            eventService.enqueuePromotionAdvertsBatchEvents(cabinet.getId(), payload, ids, event.getTriggerSource());
            return WbApiEventExecutionResult.completedSuccessfully();
        } catch (WbApiUnauthorizedScopeException e) {
            return WbApiEventExecutionResult.finalError(e.getMessage());
        } catch (Exception e) {
            return WbApiEventExecutionResult.retryableError(e.getMessage());
        }
    }
}
