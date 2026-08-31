package ru.oparin.solution.service.events.enqueue;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.oparin.solution.model.Cabinet;
import ru.oparin.solution.model.CabinetTokenType;
import ru.oparin.solution.model.WbApiEvent;
import ru.oparin.solution.model.WbApiEventType;
import ru.oparin.solution.service.events.WbApiEventExecutors;
import ru.oparin.solution.service.events.WbApiEventWriter;
import ru.oparin.solution.service.events.payload.WbAbTestApplyPhotoPayload;
import ru.oparin.solution.service.events.payload.WbAbTestStartPayload;
import ru.oparin.solution.service.events.payload.WbAbTestStartStep;
import ru.oparin.solution.service.events.payload.WbAbTestStatsPollPayload;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Постановка событий А/Б-тестов WB.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WbAbTestEventEnqueue {

    private static final int MAX_ATTEMPTS = 5;
    private static final int PRIORITY = 92;

    private final WbApiEventWriter writer;

    /**
     * Первый шаг старта А/Б-теста ({@code RESOLVE_CARD}).
     *
     * @return id события или null, если уже в очереди
     */
    @Transactional
    public Long enqueueWbAbTestStart(Long cabinetId, Long abTestId, String triggerSource) {
        return enqueueWbAbTestStartStep(
                cabinetId,
                WbAbTestStartPayload.builder()
                        .abTestId(abTestId)
                        .step(WbAbTestStartStep.RESOLVE_CARD)
                        .build(),
                triggerSource,
                LocalDateTime.now()
        );
    }

    /**
     * Следующий шаг старта А/Б с паузой под лимит media Content API.
     */
    @Transactional
    public Long enqueueNextWbAbTestStartStep(Long cabinetId, WbAbTestStartPayload payload, String triggerSource) {
        Cabinet cabinet = writer.requireCabinet(cabinetId);
        CabinetTokenType tokenType = cabinet.getTokenType() != null ? cabinet.getTokenType() : CabinetTokenType.BASIC;
        long delayMs = WbApiEventType.CONTENT_MEDIA_FILE.getRequestDelayMs(tokenType);
        LocalDateTime nextAttemptAt = LocalDateTime.now().plusNanos(delayMs * 1_000_000L);
        log.info(
                "Запланирован следующий шаг А/Б-старта: cabinetId={}, abTestId={}, step={}, variantId={}, delayMs={}, nextAttemptAt={}",
                cabinetId,
                payload.abTestId(),
                payload.resolvedStep(),
                payload.variantId(),
                delayMs,
                nextAttemptAt
        );
        return enqueueWbAbTestStartStep(cabinet, payload, triggerSource, nextAttemptAt);
    }

    /**
     * Смена главного фото А/Б-теста.
     *
     * @return id события или null, если уже в очереди
     */
    @Transactional
    public Long enqueueWbAbTestApplyPhoto(
            Long cabinetId,
            Long abTestId,
            Long variantId,
            String reason,
            boolean finishAfterApply,
            String triggerSource
    ) {
        String dedupKey = "AB_TEST_APPLY:" + cabinetId + ":" + abTestId + ":" + variantId + ":" + (finishAfterApply ? "F" : "R");
        WbAbTestApplyPhotoPayload payload = WbAbTestApplyPhotoPayload.builder()
                .abTestId(abTestId)
                .variantId(variantId)
                .reason(reason)
                .finishAfterApply(finishAfterApply)
                .build();
        return writer.insertIfAbsent(
                cabinetId,
                WbApiEventType.AB_TEST_APPLY_PHOTO,
                WbApiEventExecutors.AB_TEST_APPLY_PHOTO,
                payload,
                dedupKey,
                MAX_ATTEMPTS,
                PRIORITY,
                triggerSource,
                null
        ).map(WbApiEvent::getId).orElse(null);
    }

    /**
     * Опрос fullstats для А/Б-теста.
     *
     * @return id события или null, если уже в очереди
     */
    @Transactional
    public Long enqueueWbAbTestStatsPoll(Long cabinetId, Long abTestId, String triggerSource) {
        String dedupKey = "AB_TEST_STATS:" + cabinetId + ":" + abTestId;
        WbAbTestStatsPollPayload payload = WbAbTestStatsPollPayload.builder().abTestId(abTestId).build();
        return writer.insertIfAbsent(
                cabinetId,
                WbApiEventType.AB_TEST_STATS_POLL,
                WbApiEventExecutors.AB_TEST_STATS_POLL,
                payload,
                dedupKey,
                MAX_ATTEMPTS,
                PRIORITY - 5,
                triggerSource,
                null
        ).map(WbApiEvent::getId).orElse(null);
    }

    private Long enqueueWbAbTestStartStep(
            Long cabinetId,
            WbAbTestStartPayload payload,
            String triggerSource,
            LocalDateTime nextAttemptAt
    ) {
        return enqueueWbAbTestStartStep(writer.requireCabinet(cabinetId), payload, triggerSource, nextAttemptAt);
    }

    private Long enqueueWbAbTestStartStep(
            Cabinet cabinet,
            WbAbTestStartPayload payload,
            String triggerSource,
            LocalDateTime nextAttemptAt
    ) {
        WbAbTestStartStep step = payload.resolvedStep();
        long variantKey = payload.variantId() != null ? payload.variantId() : 0L;
        String dedupKey = "AB_TEST_START:" + cabinet.getId() + ":" + payload.abTestId() + ":" + step + ":" + variantKey;
        String legacyDedupKey = "AB_TEST_START:" + cabinet.getId() + ":" + payload.abTestId();
        if (writer.existsActive(dedupKey)
                || (step == WbAbTestStartStep.RESOLVE_CARD && writer.existsActive(legacyDedupKey))) {
            log.debug("AB_TEST_START шаг уже в очереди (dedupKey={})", dedupKey);
            return null;
        }
        WbAbTestStartPayload normalized = WbAbTestStartPayload.builder()
                .abTestId(payload.abTestId())
                .step(step)
                .variantId(payload.variantId())
                .build();
        Optional<WbApiEvent> created = writer.insertIfAbsent(
                cabinet,
                WbApiEventType.AB_TEST_START,
                WbApiEventExecutors.AB_TEST_START,
                normalized,
                dedupKey,
                MAX_ATTEMPTS,
                PRIORITY,
                triggerSource,
                nextAttemptAt
        );
        created.ifPresent(event -> log.info(
                "Создано событие шага А/Б-старта: eventId={}, cabinetId={}, abTestId={}, step={}, variantId={}, nextAttemptAt={}",
                event.getId(),
                cabinet.getId(),
                payload.abTestId(),
                step,
                payload.variantId(),
                nextAttemptAt
        ));
        return created.map(WbApiEvent::getId).orElse(null);
    }
}
