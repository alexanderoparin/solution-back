package ru.oparin.solution.service.events;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.oparin.solution.exception.WbApiUnauthorizedScopeException;
import ru.oparin.solution.exception.WbRateLimitDeferException;
import ru.oparin.solution.model.WbApiEvent;
import ru.oparin.solution.service.CabinetService;
import ru.oparin.solution.service.WbPromotionCampaignControlWriteService;
import ru.oparin.solution.service.campaign.WbCampaignScheduleControlNotifier;
import ru.oparin.solution.service.events.payload.WbPromotionCampaignControlPayload;
import ru.oparin.solution.service.sync.WbPromotionCampaignSyncService;
import ru.oparin.solution.service.wb.WbPromotionApiClient;

import java.util.List;

/**
 * Выполняет паузу рекламной кампании через WB API и обновляет данные в БД.
 */
@Component("promotionCampaignPauseEventExecutor")
@RequiredArgsConstructor
public class WbPromotionCampaignPauseEventExecutor implements WbApiEventExecutor {

    private final WbApiEventService eventService;
    private final CabinetService cabinetService;
    private final WbPromotionApiClient promotionApiClient;
    private final WbPromotionCampaignSyncService promotionCampaignSyncService;
    private final WbPromotionCampaignControlWriteService promotionControlWriteService;
    private final WbCampaignScheduleControlNotifier scheduleControlNotifier;

    @Override
    public WbApiEventExecutionResult execute(WbApiEvent event) {
        WbPromotionCampaignControlPayload payload = eventService.readPayload(event, WbPromotionCampaignControlPayload.class);
        if (payload.advertId() == null) {
            return WbApiEventExecutionResult.finalError("Не указан ID кампании");
        }
        var cabinet = cabinetService.findByIdWithUserOrThrow(event.getCabinet().getId());
        if (cabinet.getApiKey() == null || cabinet.getApiKey().isBlank()) {
            return WbApiEventExecutionResult.finalError("У кабинета отсутствует API ключ");
        }
        try {
            promotionApiClient.pauseCampaign(cabinet.getApiKey(), payload.advertId());
            promotionCampaignSyncService.loadAndSaveAdvertsBatch(
                    cabinet, cabinet.getApiKey(), List.of(payload.advertId()));
            promotionControlWriteService.clearBlock(cabinet.getId());
            scheduleControlNotifier.onPauseSucceededOnWb(payload.advertId(), cabinet.getId());
            return WbApiEventExecutionResult.completedSuccessfully();
        } catch (WbApiUnauthorizedScopeException e) {
            if (WbPromotionCampaignControlWriteService.isReadOnlyTokenError(e)) {
                promotionControlWriteService.recordReadOnlyTokenBlock(cabinet.getId());
                return WbApiEventExecutionResult.finalError(WbPromotionCampaignControlWriteService.READ_ONLY_USER_MESSAGE);
            }
            return WbApiEventExecutionResult.finalError(e.getMessage());
        } catch (WbRateLimitDeferException e) {
            return WbEventExecutionErrors.fromDeferException(e);
        } catch (org.springframework.web.client.RestClientException e) {
            WbApiEventExecutionResult deferResult = WbEventExecutionErrors.deferResultIfPresent(e);
            if (deferResult != null) {
                return deferResult;
            }
            if (WbPromotionCampaignControlWriteService.isReadOnlyTokenError(e)) {
                promotionControlWriteService.recordReadOnlyTokenBlock(cabinet.getId());
                return WbApiEventExecutionResult.finalError(WbPromotionCampaignControlWriteService.READ_ONLY_USER_MESSAGE);
            }
            if (e.getMessage() != null && !e.getMessage().contains("429")) {
                return WbApiEventExecutionResult.finalError(e.getMessage());
            }
            return WbEventExecutionErrors.wrapRestClientException(e);
        } catch (Exception e) {
            return WbEventExecutionErrors.wrapDeferOrRetryable(e);
        }
    }
}
