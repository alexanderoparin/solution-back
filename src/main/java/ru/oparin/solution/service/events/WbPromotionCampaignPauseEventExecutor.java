package ru.oparin.solution.service.events;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import ru.oparin.solution.exception.WbApiUnauthorizedScopeException;
import ru.oparin.solution.exception.WbRateLimitDeferException;
import ru.oparin.solution.model.Cabinet;
import ru.oparin.solution.model.WbApiEvent;
import ru.oparin.solution.service.CabinetService;
import ru.oparin.solution.service.WbPromotionCampaignControlService;
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
@Slf4j
public class WbPromotionCampaignPauseEventExecutor implements WbApiEventExecutor {

    private final WbApiEventService eventService;
    private final CabinetService cabinetService;
    private final WbPromotionApiClient promotionApiClient;
    private final WbPromotionCampaignSyncService promotionCampaignSyncService;
    private final WbPromotionCampaignControlWriteService promotionControlWriteService;
    private final WbPromotionCampaignControlService promotionCampaignControlService;
    private final WbCampaignScheduleControlNotifier scheduleControlNotifier;

    @Override
    public WbApiEventExecutionResult execute(WbApiEvent event) {
        WbPromotionCampaignControlPayload payload = eventService.readPayload(event, WbPromotionCampaignControlPayload.class);
        if (payload.advertId() == null) {
            return WbApiEventExecutionResult.finalError("Не указан ID кампании");
        }
        Cabinet cabinet = cabinetService.findByIdWithUserOrThrow(event.getCabinet().getId());
        if (cabinet.getApiKey() == null || cabinet.getApiKey().isBlank()) {
            return WbApiEventExecutionResult.finalError("У кабинета отсутствует API ключ");
        }
        try {
            promotionApiClient.pauseCampaign(cabinet.getApiKey(), payload.advertId());
            return completePauseSuccess(cabinet, payload.advertId());
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
            if (WbPromotionCampaignControlService.isStatusUnchangedError(e.getMessage())) {
                return completePauseAsAlreadyDone(cabinet, payload.advertId(), e.getMessage());
            }
            if (e.getMessage() != null && !e.getMessage().contains("429")) {
                return WbApiEventExecutionResult.finalError(e.getMessage());
            }
            return WbEventExecutionErrors.wrapRestClientException(e);
        } catch (Exception e) {
            if (WbPromotionCampaignControlService.isStatusUnchangedError(e.getMessage())) {
                return completePauseAsAlreadyDone(cabinet, payload.advertId(), e.getMessage());
            }
            return WbEventExecutionErrors.wrapDeferOrRetryable(e);
        }
    }

    /**
     * Успешная пауза: синхронизация статуса и уведомление планировщика.
     */
    private WbApiEventExecutionResult completePauseSuccess(Cabinet cabinet, Long advertId) {
        promotionCampaignSyncService.loadAndSaveAdvertsBatch(
                cabinet, cabinet.getApiKey(), List.of(advertId));
        promotionControlWriteService.clearBlock(cabinet.getId());
        scheduleControlNotifier.onPauseSucceededOnWb(advertId, cabinet.getId());
        return WbApiEventExecutionResult.completedSuccessfully();
    }

    /**
     * WB уже не даёт сменить статус (РК не ACTIVE) — цель паузы достигнута, подтягиваем статус в БД.
     */
    private WbApiEventExecutionResult completePauseAsAlreadyDone(Cabinet cabinet, Long advertId, String wbMessage) {
        log.info(
                "Пауза advertId={} cabinetId={}: Status Unchanged — считаем успехом и синхронизируем статус. WB: {}",
                advertId,
                cabinet.getId(),
                wbMessage
        );
        try {
            promotionCampaignControlService.reconcileCampaignStatusFromWb(cabinet, advertId);
        } catch (Exception syncError) {
            log.warn(
                    "Не удалось синхронизировать статус после Status Unchanged advertId={}: {}",
                    advertId,
                    syncError.getMessage()
            );
            return WbApiEventExecutionResult.finalError(
                    "Status Unchanged на WB, но не удалось обновить статус РК: " + syncError.getMessage());
        }
        scheduleControlNotifier.onPauseSucceededOnWb(advertId, cabinet.getId());
        return WbApiEventExecutionResult.completedSuccessfully();
    }
}
