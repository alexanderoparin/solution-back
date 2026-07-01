package ru.oparin.solution.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.oparin.solution.dto.analytics.CampaignControlEnqueueResponse;
import ru.oparin.solution.dto.analytics.ScheduleControlAttemptResult;
import ru.oparin.solution.exception.WbApiUnauthorizedScopeException;
import ru.oparin.solution.model.*;
import ru.oparin.solution.repository.PromotionCampaignRepository;
import ru.oparin.solution.service.events.WbApiEventService;
import ru.oparin.solution.service.events.WbEventRateLimitService;
import ru.oparin.solution.service.sync.PromotionCampaignSyncService;
import ru.oparin.solution.service.wb.WbPromotionApiClient;

import java.util.List;
import java.util.Set;

/**
 * Постановка в очередь управления рекламными кампаниями (запуск / пауза) с учётом лимитов WB API.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PromotionCampaignControlService {

    public static final String TRIGGER_UI_START = "BIDDER_UI_START";
    public static final String TRIGGER_UI_PAUSE = "BIDDER_UI_PAUSE";
    public static final String TRIGGER_SCHEDULE_START = "BIDDER_SCHEDULE_START";
    public static final String TRIGGER_SCHEDULE_PAUSE = "BIDDER_SCHEDULE_PAUSE";

    private static final Set<CampaignStatus> START_ALLOWED = Set.of(
            CampaignStatus.READY_TO_START,
            CampaignStatus.PAUSED
    );
    private static final Set<CampaignStatus> PAUSE_ALLOWED = Set.of(CampaignStatus.ACTIVE);

    private final PromotionCampaignRepository campaignRepository;
    private final WbApiEventService wbApiEventService;
    private final WbEventRateLimitService rateLimitService;
    private final PromotionCampaignControlWriteService promotionControlWriteService;
    private final WbPromotionApiClient promotionApiClient;
    private final PromotionCampaignSyncService promotionCampaignSyncService;

    /**
     * Ставит в очередь запуск кампании (ручное управление / UI).
     *
     * @throws CampaignControlRateLimitException если лимит WB API не позволяет запрос сейчас
     * @throws IllegalArgumentException если кампания не найдена или статус не допускает запуск
     */
    public CampaignControlEnqueueResponse enqueueStart(Cabinet cabinet, Long advertId) {
        return enqueueStart(cabinet, advertId, TRIGGER_UI_START, true);
    }

    /**
     * Постановка запуска из планировщика: rate-limit соблюдается при исполнении в {@code WbApiEventDispatcher}.
     */
    public CampaignControlEnqueueResponse enqueueStartFromSchedule(Cabinet cabinet, Long advertId) {
        return enqueueStart(cabinet, advertId, TRIGGER_SCHEDULE_START, false);
    }

    /**
     * Запуск по расписанию: для персонального токена — сразу HTTP к WB; при неуспехе — событие в очередь.
     */
    public ScheduleControlAttemptResult attemptStartFromSchedule(Cabinet cabinet, Long advertId) {
        try {
            validateApiKey(cabinet);
            promotionControlWriteService.ensureControlAllowed(cabinet);
            PromotionCampaign campaign = findCampaignOrThrow(cabinet.getId(), advertId);
            validateStatusForStart(campaign);
        } catch (IllegalArgumentException e) {
            return ScheduleControlAttemptResult.failed(e.getMessage());
        }
        if (!usesDirectScheduleControl(cabinet)) {
            return toScheduleResult(enqueueStart(cabinet, advertId, TRIGGER_SCHEDULE_START, false));
        }
        try {
            applyStartOnWb(cabinet, advertId);
            return ScheduleControlAttemptResult.directSuccess();
        } catch (WbApiUnauthorizedScopeException e) {
            return handleUnauthorizedForSchedule(cabinet, e);
        } catch (Exception e) {
            if (PromotionCampaignControlWriteService.isReadOnlyTokenError(e)) {
                promotionControlWriteService.recordReadOnlyTokenBlock(cabinet.getId());
                return ScheduleControlAttemptResult.failed(PromotionCampaignControlWriteService.READ_ONLY_USER_MESSAGE);
            }
            log.debug("Прямой запуск по расписанию advertId={} не удался, очередь: {}", advertId, e.getMessage());
            return toScheduleResult(enqueueStart(cabinet, advertId, TRIGGER_SCHEDULE_START, false));
        }
    }

    private CampaignControlEnqueueResponse enqueueStart(
            Cabinet cabinet,
            Long advertId,
            String triggerSource,
            boolean checkRateLimitBeforeEnqueue
    ) {
        validateApiKey(cabinet);
        promotionControlWriteService.ensureControlAllowed(cabinet);
        PromotionCampaign campaign = findCampaignOrThrow(cabinet.getId(), advertId);
        validateStatusForStart(campaign);
        if (checkRateLimitBeforeEnqueue) {
            checkRateLimit(cabinet, WbApiEventType.PROMOTION_CAMPAIGN_START);
        }
        Long eventId = wbApiEventService.enqueuePromotionCampaignStart(cabinet.getId(), advertId, triggerSource);
        if (eventId == null) {
            return new CampaignControlEnqueueResponse(
                    false, null, "Запуск этой кампании уже выполняется или ожидает в очереди");
        }
        return new CampaignControlEnqueueResponse(true, eventId, "Запуск кампании поставлен в очередь");
    }

    /**
     * Ставит в очередь паузу кампании (ручное управление).
     *
     * @throws CampaignControlRateLimitException если лимит WB API не позволяет запрос сейчас
     * @throws IllegalArgumentException если кампания не найдена или статус не допускает паузу
     */
    public CampaignControlEnqueueResponse enqueuePause(Cabinet cabinet, Long advertId) {
        return enqueuePause(cabinet, advertId, TRIGGER_UI_PAUSE, true);
    }

    /**
     * Постановка паузы из планировщика — rate-limit на исполнении в очереди WB.
     */
    public CampaignControlEnqueueResponse enqueuePauseFromSchedule(Cabinet cabinet, Long advertId) {
        return enqueuePause(cabinet, advertId, TRIGGER_SCHEDULE_PAUSE, false);
    }

    /**
     * Пауза по расписанию: для персонального токена — сразу HTTP к WB; при неуспехе — событие в очередь.
     */
    public ScheduleControlAttemptResult attemptPauseFromSchedule(Cabinet cabinet, Long advertId) {
        try {
            validateApiKey(cabinet);
            promotionControlWriteService.ensureControlAllowed(cabinet);
            PromotionCampaign campaign = findCampaignOrThrow(cabinet.getId(), advertId);
            validateStatusForPause(campaign);
        } catch (IllegalArgumentException e) {
            return ScheduleControlAttemptResult.failed(e.getMessage());
        }
        if (!usesDirectScheduleControl(cabinet)) {
            return toScheduleResult(enqueuePause(cabinet, advertId, TRIGGER_SCHEDULE_PAUSE, false));
        }
        try {
            applyPauseOnWb(cabinet, advertId);
            return ScheduleControlAttemptResult.directSuccess();
        } catch (WbApiUnauthorizedScopeException e) {
            return handleUnauthorizedForSchedule(cabinet, e);
        } catch (Exception e) {
            if (PromotionCampaignControlWriteService.isReadOnlyTokenError(e)) {
                promotionControlWriteService.recordReadOnlyTokenBlock(cabinet.getId());
                return ScheduleControlAttemptResult.failed(PromotionCampaignControlWriteService.READ_ONLY_USER_MESSAGE);
            }
            log.debug("Прямая пауза по расписанию advertId={} не удалась, очередь: {}", advertId, e.getMessage());
            return toScheduleResult(enqueuePause(cabinet, advertId, TRIGGER_SCHEDULE_PAUSE, false));
        }
    }

    private CampaignControlEnqueueResponse enqueuePause(
            Cabinet cabinet,
            Long advertId,
            String triggerSource,
            boolean checkRateLimitBeforeEnqueue
    ) {
        validateApiKey(cabinet);
        promotionControlWriteService.ensureControlAllowed(cabinet);
        PromotionCampaign campaign = findCampaignOrThrow(cabinet.getId(), advertId);
        validateStatusForPause(campaign);
        if (checkRateLimitBeforeEnqueue) {
            checkRateLimit(cabinet, WbApiEventType.PROMOTION_CAMPAIGN_PAUSE);
        }
        Long eventId = wbApiEventService.enqueuePromotionCampaignPause(cabinet.getId(), advertId, triggerSource);
        if (eventId == null) {
            return new CampaignControlEnqueueResponse(
                    false, null, "Пауза этой кампании уже выполняется или ожидает в очереди");
        }
        return new CampaignControlEnqueueResponse(true, eventId, "Пауза кампании поставлена в очередь");
    }

    private void validateApiKey(Cabinet cabinet) {
        if (cabinet.getApiKey() == null || cabinet.getApiKey().isBlank()) {
            throw new IllegalArgumentException("У кабинета нет API-ключа");
        }
    }

    private PromotionCampaign findCampaignOrThrow(Long cabinetId, Long advertId) {
        return campaignRepository.findByAdvertIdAndCabinet_Id(advertId, cabinetId)
                .orElseThrow(() -> new IllegalArgumentException("Кампания не найдена в этом кабинете"));
    }

    private void validateStatusForStart(PromotionCampaign campaign) {
        CampaignStatus status = campaign.getStatus();
        if (status == null || !START_ALLOWED.contains(status)) {
            throw new IllegalArgumentException(
                    "Запуск доступен только для кампаний «Готово к запуску» или «На паузе»");
        }
    }

    private void validateStatusForPause(PromotionCampaign campaign) {
        CampaignStatus status = campaign.getStatus();
        if (status == null || !PAUSE_ALLOWED.contains(status)) {
            throw new IllegalArgumentException("Пауза доступна только для активных кампаний");
        }
    }

    private void checkRateLimit(Cabinet cabinet, WbApiEventType eventType) {
        CabinetTokenType tokenType = cabinet.getTokenType() != null
                ? cabinet.getTokenType()
                : CabinetTokenType.BASIC;
        long seconds = rateLimitService.secondsUntilAvailable(cabinet.getId(), eventType, tokenType);
        if (seconds > 0) {
            throw new CampaignControlRateLimitException(seconds);
        }
    }

    private boolean usesDirectScheduleControl(Cabinet cabinet) {
        return CabinetTokenType.effective(cabinet.getTokenType()) == CabinetTokenType.PERSONAL;
    }

    private void applyStartOnWb(Cabinet cabinet, Long advertId) {
        promotionApiClient.startCampaign(cabinet.getApiKey(), advertId);
        promotionCampaignSyncService.loadAndSaveAdvertsBatch(
                cabinet, cabinet.getApiKey(), List.of(advertId));
        promotionControlWriteService.clearBlock(cabinet.getId());
    }

    private void applyPauseOnWb(Cabinet cabinet, Long advertId) {
        promotionApiClient.pauseCampaign(cabinet.getApiKey(), advertId);
        promotionCampaignSyncService.loadAndSaveAdvertsBatch(
                cabinet, cabinet.getApiKey(), List.of(advertId));
        promotionControlWriteService.clearBlock(cabinet.getId());
    }

    private ScheduleControlAttemptResult handleUnauthorizedForSchedule(
            Cabinet cabinet,
            WbApiUnauthorizedScopeException exception
    ) {
        if (PromotionCampaignControlWriteService.isReadOnlyTokenError(exception)) {
            promotionControlWriteService.recordReadOnlyTokenBlock(cabinet.getId());
            return ScheduleControlAttemptResult.failed(PromotionCampaignControlWriteService.READ_ONLY_USER_MESSAGE);
        }
        return ScheduleControlAttemptResult.failed(exception.getMessage());
    }

    private ScheduleControlAttemptResult toScheduleResult(CampaignControlEnqueueResponse response) {
        if (response.enqueued()) {
            return ScheduleControlAttemptResult.enqueued(response.eventId());
        }
        return ScheduleControlAttemptResult.skippedPending();
    }

    /**
     * Исключение: лимит WB API, повтор позже.
     */
    public static class CampaignControlRateLimitException extends RuntimeException {
        private final long nextAvailableInSeconds;

        public CampaignControlRateLimitException(long nextAvailableInSeconds) {
            super("Превышен лимит запросов к WB API");
            this.nextAvailableInSeconds = nextAvailableInSeconds;
        }

        public long getNextAvailableInSeconds() {
            return nextAvailableInSeconds;
        }
    }
}
