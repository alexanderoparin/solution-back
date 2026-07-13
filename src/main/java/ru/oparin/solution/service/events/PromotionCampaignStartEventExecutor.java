package ru.oparin.solution.service.events;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.oparin.solution.exception.WbApiUnauthorizedScopeException;
import ru.oparin.solution.exception.WbRateLimitDeferException;
import ru.oparin.solution.model.WbApiEvent;
import ru.oparin.solution.service.CabinetService;
import ru.oparin.solution.service.PromotionCampaignControlWriteService;
import ru.oparin.solution.service.campaign.CampaignScheduleControlNotifier;
import ru.oparin.solution.service.campaign.CampaignStartBudgetGuard;
import ru.oparin.solution.service.events.payload.PromotionCampaignControlPayload;
import ru.oparin.solution.service.sync.PromotionCampaignSyncService;
import ru.oparin.solution.service.wb.WbPromotionApiClient;

import java.util.List;

/**
 * Выполняет запуск рекламной кампании через WB API и обновляет данные в БД.
 */
@Component("promotionCampaignStartEventExecutor")
@RequiredArgsConstructor
public class PromotionCampaignStartEventExecutor implements WbApiEventExecutor {

    private final WbApiEventService eventService;
    private final CabinetService cabinetService;
    private final WbPromotionApiClient promotionApiClient;
    private final PromotionCampaignSyncService promotionCampaignSyncService;
    private final PromotionCampaignControlWriteService promotionControlWriteService;
    private final CampaignScheduleControlNotifier scheduleControlNotifier;
    private final CampaignStartBudgetGuard startBudgetGuard;

    @Override
    public WbApiEventExecutionResult execute(WbApiEvent event) {
        PromotionCampaignControlPayload payload = eventService.readPayload(event, PromotionCampaignControlPayload.class);
        if (payload.advertId() == null) {
            return WbApiEventExecutionResult.finalError("Не указан ID кампании");
        }
        var cabinet = cabinetService.findByIdWithUserOrThrow(event.getCabinet().getId());
        if (cabinet.getApiKey() == null || cabinet.getApiKey().isBlank()) {
            return WbApiEventExecutionResult.finalError("У кабинета отсутствует API ключ");
        }
        try {
            promotionApiClient.startCampaign(cabinet.getApiKey(), payload.advertId());
            promotionCampaignSyncService.loadAndSaveAdvertsBatch(
                    cabinet, cabinet.getApiKey(), List.of(payload.advertId()));
            promotionControlWriteService.clearBlock(cabinet.getId());
            scheduleControlNotifier.onStartSucceededOnWb(payload.advertId(), cabinet.getId());
            return WbApiEventExecutionResult.completedSuccessfully();
        } catch (WbApiUnauthorizedScopeException e) {
            if (PromotionCampaignControlWriteService.isReadOnlyTokenError(e)) {
                promotionControlWriteService.recordReadOnlyTokenBlock(cabinet.getId());
                return WbApiEventExecutionResult.finalError(PromotionCampaignControlWriteService.READ_ONLY_USER_MESSAGE);
            }
            return WbApiEventExecutionResult.finalError(e.getMessage());
        } catch (WbRateLimitDeferException e) {
            return WbEventExecutionErrors.fromDeferException(e);
        } catch (org.springframework.web.client.RestClientException e) {
            WbApiEventExecutionResult deferResult = WbEventExecutionErrors.deferResultIfPresent(e);
            if (deferResult != null) {
                return deferResult;
            }
            if (PromotionCampaignControlWriteService.isReadOnlyTokenError(e)) {
                promotionControlWriteService.recordReadOnlyTokenBlock(cabinet.getId());
                return WbApiEventExecutionResult.finalError(PromotionCampaignControlWriteService.READ_ONLY_USER_MESSAGE);
            }
            if (e.getMessage() != null && !e.getMessage().contains("429")) {
                if (CampaignStartBudgetGuard.isNoBudgetToStartError(e.getMessage())) {
                    startBudgetGuard.blockStartDueToNoBudget(payload.advertId(), cabinet.getId());
                    return WbApiEventExecutionResult.finalError(CampaignStartBudgetGuard.NO_BUDGET_USER_MESSAGE);
                }
                return WbApiEventExecutionResult.finalError(e.getMessage());
            }
            return WbEventExecutionErrors.wrapRestClientException(e);
        } catch (Exception e) {
            return WbEventExecutionErrors.wrapDeferOrRetryable(e);
        }
    }
}
