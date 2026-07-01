package ru.oparin.solution.service.events;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.oparin.solution.exception.WbApiUnauthorizedScopeException;
import ru.oparin.solution.model.WbApiEvent;
import ru.oparin.solution.service.CabinetService;
import ru.oparin.solution.service.PromotionCampaignControlWriteService;
import ru.oparin.solution.service.campaign.CampaignScheduleControlNotifier;
import ru.oparin.solution.service.events.payload.PromotionCampaignControlPayload;
import ru.oparin.solution.service.sync.PromotionCampaignSyncService;
import ru.oparin.solution.service.wb.WbPromotionApiClient;

import java.util.List;

/**
 * Выполняет паузу рекламной кампании через WB API и обновляет данные в БД.
 */
@Component("promotionCampaignPauseEventExecutor")
@RequiredArgsConstructor
public class PromotionCampaignPauseEventExecutor implements WbApiEventExecutor {

    private final WbApiEventService eventService;
    private final CabinetService cabinetService;
    private final WbPromotionApiClient promotionApiClient;
    private final PromotionCampaignSyncService promotionCampaignSyncService;
    private final PromotionCampaignControlWriteService promotionControlWriteService;
    private final CampaignScheduleControlNotifier scheduleControlNotifier;

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
            promotionApiClient.pauseCampaign(cabinet.getApiKey(), payload.advertId());
            promotionCampaignSyncService.loadAndSaveAdvertsBatch(
                    cabinet, cabinet.getApiKey(), List.of(payload.advertId()));
            promotionControlWriteService.clearBlock(cabinet.getId());
            scheduleControlNotifier.onPauseSucceededOnWb(payload.advertId(), cabinet.getId());
            return WbApiEventExecutionResult.completedSuccessfully();
        } catch (WbApiUnauthorizedScopeException e) {
            if (PromotionCampaignControlWriteService.isReadOnlyTokenError(e)) {
                promotionControlWriteService.recordReadOnlyTokenBlock(cabinet.getId());
                return WbApiEventExecutionResult.finalError(PromotionCampaignControlWriteService.READ_ONLY_USER_MESSAGE);
            }
            return WbApiEventExecutionResult.finalError(e.getMessage());
        } catch (org.springframework.web.client.RestClientException e) {
            if (PromotionCampaignControlWriteService.isReadOnlyTokenError(e)) {
                promotionControlWriteService.recordReadOnlyTokenBlock(cabinet.getId());
                return WbApiEventExecutionResult.finalError(PromotionCampaignControlWriteService.READ_ONLY_USER_MESSAGE);
            }
            if (e.getMessage() != null && !e.getMessage().contains("429")) {
                return WbApiEventExecutionResult.finalError(e.getMessage());
            }
            return WbEventExecutionErrors.wrapDeferOrRetryable(e);
        } catch (Exception e) {
            return WbEventExecutionErrors.wrapDeferOrRetryable(e);
        }
    }
}
