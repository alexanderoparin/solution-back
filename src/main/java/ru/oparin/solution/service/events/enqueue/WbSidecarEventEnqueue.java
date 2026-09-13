package ru.oparin.solution.service.events.enqueue;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.oparin.solution.model.Cabinet;
import ru.oparin.solution.model.CabinetTokenType;
import ru.oparin.solution.model.WbApiEvent;
import ru.oparin.solution.model.WbApiEventType;
import ru.oparin.solution.service.CabinetIntegrationMirrorService;
import ru.oparin.solution.service.events.WbApiEventExecutors;
import ru.oparin.solution.service.events.WbApiEventWriter;
import ru.oparin.solution.service.events.payload.WbItemRatingSyncStepPayload;
import ru.oparin.solution.service.events.payload.WbMainStepPayload;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Побочные события волны: item-rating и календарь акций.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WbSidecarEventEnqueue {

    private static final int MAX_ATTEMPTS = 5;
    private static final int PRIORITY = 84;

    private final WbApiEventWriter writer;
    private final CabinetIntegrationMirrorService cabinetIntegrationMirrorService;

    /**
     * Первый шаг item-rating sync (BASIC-токен пропускается).
     */
    @Transactional
    public void enqueueItemRatingSyncCabinetEvent(Long cabinetId, WbMainStepPayload payload, String triggerSource) {
        String cabinetDedupKey = "ITEM_RATING_SYNC_CABINET:" + cabinetId + ":" + payload.dateFrom() + ":" + payload.dateTo();
        if (writer.existsActive(cabinetDedupKey)) {
            log.debug("WB API item-rating sync уже существует (dedupKey={}), создание пропущено", cabinetDedupKey);
            return;
        }
        Cabinet cabinet = requireCabinetWithTokenOverlay(cabinetId);
        if (!CabinetTokenType.effective(cabinet.getTokenType()).supportsItemRating()) {
            log.debug("Пропуск item-rating sync: cabinetId={}, tokenType={}",
                    cabinetId, CabinetTokenType.effective(cabinet.getTokenType()));
            return;
        }
        WbItemRatingSyncStepPayload stepPayload = WbItemRatingSyncStepPayload.builder()
                .offset(0)
                .syncStartedAt(LocalDateTime.now())
                .dateFrom(payload.dateFrom())
                .dateTo(payload.dateTo())
                .includeStocks(payload.includeStocks())
                .build();
        // Первый шаг дедупим на уровне кабинета+период (тот же ключ, что в existsActive выше).
        enqueueItemRatingStepEvent(cabinet, stepPayload, triggerSource, LocalDateTime.now(), PRIORITY, cabinetDedupKey);
    }

    /**
     * Следующий шаг item-rating с задержкой под лимит API.
     */
    @Transactional
    public void enqueueNextItemRatingStepEvent(
            Long cabinetId,
            WbItemRatingSyncStepPayload payload,
            String triggerSource
    ) {
        Cabinet cabinet = requireCabinetWithTokenOverlay(cabinetId);
        if (!CabinetTokenType.effective(cabinet.getTokenType()).supportsItemRating()) {
            log.debug("Пропуск следующего шага item-rating: cabinetId={}, tokenType={}",
                    cabinetId, CabinetTokenType.effective(cabinet.getTokenType()));
            return;
        }
        CabinetTokenType tokenType = CabinetTokenType.effective(cabinet.getTokenType());
        long delayMs = WbApiEventType.ANALYTICS_ITEM_RATING_CABINET.getRequestDelayMs(tokenType);
        LocalDateTime nextAttemptAt = LocalDateTime.now().plusNanos(delayMs * 1_000_000L);
        log.info("Запланирован следующий шаг item-rating: cabinetId={}, offset={}, delayMs={}, nextAttemptAt={}",
                cabinetId, payload.offset(), delayMs, nextAttemptAt);
        String stepDedupKey = "ITEM_RATING_SYNC_STEP:"
                + cabinet.getId() + ":"
                + payload.syncStartedAt() + ":"
                + payload.offset();
        enqueueItemRatingStepEvent(cabinet, payload, triggerSource, nextAttemptAt, PRIORITY, stepDedupKey);
    }

    /**
     * Кабинет с overlay credentials/tokenType из cabinet_integrations
     * ({@code tokenType} на сущности — {@code @Transient}, без overlay всегда BASIC).
     */
    private Cabinet requireCabinetWithTokenOverlay(Long cabinetId) {
        Cabinet cabinet = writer.requireCabinet(cabinetId);
        cabinetIntegrationMirrorService.overlayOntoCabinet(cabinet);
        return cabinet;
    }

    /**
     * Синхронизация календаря акций кабинета.
     */
    @Transactional
    public void enqueuePromotionCalendarSyncCabinetEvent(Long cabinetId, WbMainStepPayload payload, String triggerSource) {
        String dedupKey = "PROMOTION_CALENDAR_SYNC_CABINET:" + cabinetId + ":" + payload.dateFrom() + ":" + payload.dateTo();
        writer.insertIfAbsent(
                cabinetId,
                WbApiEventType.PROMOTION_CALENDAR_SYNC_CABINET,
                WbApiEventExecutors.PROMOTION_CALENDAR_SYNC,
                payload,
                dedupKey,
                MAX_ATTEMPTS,
                PRIORITY,
                triggerSource,
                null
        );
    }

    private void enqueueItemRatingStepEvent(
            Cabinet cabinet,
            WbItemRatingSyncStepPayload payload,
            String triggerSource,
            LocalDateTime nextAttemptAt,
            int priority,
            String dedupKey
    ) {
        Optional<WbApiEvent> created = writer.insertIfAbsent(
                cabinet,
                WbApiEventType.ANALYTICS_ITEM_RATING_CABINET,
                WbApiEventExecutors.ITEM_RATING_SYNC,
                payload,
                dedupKey,
                MAX_ATTEMPTS,
                priority,
                triggerSource,
                nextAttemptAt
        );
        created.ifPresent(event -> log.info(
                "Создано событие шага item-rating: eventId={}, cabinetId={}, offset={}, nextAttemptAt={}",
                event.getId(), cabinet.getId(), payload.offset(), nextAttemptAt));
    }
}
