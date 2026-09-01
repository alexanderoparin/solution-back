package ru.oparin.solution.service.events;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.oparin.solution.config.WbEventsProperties;
import ru.oparin.solution.model.WbApiEvent;
import ru.oparin.solution.model.WbApiEventStatus;
import ru.oparin.solution.model.WbApiEventType;
import ru.oparin.solution.repository.WbApiEventRepository;
import ru.oparin.solution.service.CabinetSyncStateService;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Жизненный цикл очереди WB API событий: poll, статусы, recover, retry, cleanup.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WbApiEventQueueService {

    private static final List<WbApiEventStatus> RUNNABLE_STATUSES = List.of(
            WbApiEventStatus.CREATED,
            WbApiEventStatus.FAILED_RETRYABLE,
            WbApiEventStatus.DEFERRED_RATE_LIMIT
    );

    private static final List<WbApiEventType> MAIN_EVENT_TYPES = List.of(
            WbApiEventType.ANALYTICS_SALES_FUNNEL_NMID,
            WbApiEventType.PRICES_CABINET_WITH_SPP,
            WbApiEventType.PROMOTION_COUNT,
            WbApiEventType.PROMOTION_ADVERTS_BATCH,
            WbApiEventType.PROMOTION_STATS_BATCH,
            WbApiEventType.PROMOTION_NORMQUERY_STATS_BATCH,
            WbApiEventType.ANALYTICS_ITEM_RATING_CABINET,
            WbApiEventType.PROMOTION_CALENDAR_SYNC_CABINET
    );

    private final WbApiEventRepository eventRepository;
    private final CabinetSyncStateService cabinetSyncStateService;
    private final WbEventsProperties wbEventsProperties;

    /**
     * Due-события для poll: не больше одного на пару (кабинет, тип события).
     */
    @Transactional(readOnly = true)
    public List<WbApiEvent> findDueEvents() {
        List<WbApiEventStatus> statuses = List.of(
                WbApiEventStatus.CREATED,
                WbApiEventStatus.FAILED_RETRYABLE,
                WbApiEventStatus.DEFERRED_RATE_LIMIT
        );
        List<String> statusNames = statuses.stream().map(Enum::name).toList();
        List<Long> ids = eventRepository.findReadyEventIdsOnePerCabinetAndType(statusNames, LocalDateTime.now());
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        Map<Long, WbApiEvent> byId = eventRepository.findAllByIdInWithCabinet(ids).stream()
                .collect(Collectors.toMap(WbApiEvent::getId, e -> e, (a, b) -> a));
        List<WbApiEvent> sorted = ids.stream()
                .map(byId::get)
                .filter(Objects::nonNull)
                .sorted(Comparator
                        .comparing(WbApiEvent::getPriority, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(WbApiEvent::getNextAttemptAt, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(WbApiEvent::getId, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
        int batchSize = Math.max(1, wbEventsProperties.getPollBatchSize());
        if (sorted.size() <= batchSize) {
            return sorted;
        }
        return sorted.subList(0, batchSize);
    }

    /**
     * Атомарно переводит событие в RUNNING, если оно ещё runnable.
     */
    @Transactional
    public boolean tryMarkRunning(WbApiEvent event) {
        int updated = eventRepository.tryMarkRunning(
                event.getId(),
                event.getCabinet().getId(),
                event.getEventType(),
                RUNNABLE_STATUSES,
                WbApiEventStatus.RUNNING,
                LocalDateTime.now()
        );
        return updated > 0;
    }

    /**
     * После таймаута выполнения с момента {@code tryMarkRunning} событие могло остаться RUNNING — переводим в retry.
     */
    @Transactional
    public boolean revertRunningAfterExecutionTimeout(long eventId, int executionTimeoutSeconds) {
        WbApiEvent event = eventRepository.findById(eventId).orElse(null);
        if (event == null || event.getStatus() != WbApiEventStatus.RUNNING) {
            return false;
        }
        event.setStartedAt(null);
        markFailed(
                event,
                WbApiEventExecutionResult.retryableError(
                        "Таймаут выполнения (" + executionTimeoutSeconds + " с) с момента старта."
                )
        );
        return true;
    }

    /**
     * Успех только если событие ещё в RUNNING (иначе таймаут выполнения уже перевёл в retry).
     */
    @Transactional
    public void markSuccessIfRunning(Long eventId) {
        WbApiEvent event = eventRepository.findById(eventId).orElse(null);
        if (event == null || event.getStatus() != WbApiEventStatus.RUNNING) {
            return;
        }
        markSuccess(event);
    }

    /**
     * Ошибка выполнения только если событие ещё в RUNNING.
     */
    @Transactional
    public void markFailedIfRunning(Long eventId, WbApiEventExecutionResult result) {
        WbApiEvent event = eventRepository.findById(eventId).orElse(null);
        if (event == null || event.getStatus() != WbApiEventStatus.RUNNING) {
            return;
        }
        event.setStartedAt(null);
        markFailed(event, result);
    }

    /**
     * RUNNING дольше timeoutMinutes → FAILED_RETRYABLE.
     */
    @Transactional
    public int recoverStuckRunningEvents(int timeoutMinutes) {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(timeoutMinutes);
        List<WbApiEvent> stuck = eventRepository.findByStatusAndStartedAtBefore(WbApiEventStatus.RUNNING, threshold);
        for (WbApiEvent event : stuck) {
            event.setStatus(WbApiEventStatus.FAILED_RETRYABLE);
            event.setLastError("Автовосстановление: событие было RUNNING дольше " + timeoutMinutes + " мин");
            event.setStartedAt(null);
            event.setNextAttemptAt(LocalDateTime.now().plusSeconds(10));
            event.setUpdatedAt(LocalDateTime.now());
        }
        eventRepository.saveAll(stuck);
        return stuck.size();
    }

    /**
     * После остановки JVM события могли остаться в RUNNING. Переводим их в повтор без увеличения счётчика попыток.
     */
    @Transactional
    public int recoverRunningEventsAfterJvmStop() {
        List<WbApiEvent> running = eventRepository.findByStatus(WbApiEventStatus.RUNNING);
        if (running.isEmpty()) {
            return 0;
        }
        LocalDateTime now = LocalDateTime.now();
        for (WbApiEvent event : running) {
            event.setStatus(WbApiEventStatus.FAILED_RETRYABLE);
            event.setStartedAt(null);
            event.setLastError("Запуск приложения: сброс RUNNING после остановки процесса");
            event.setNextAttemptAt(now);
            event.setUpdatedAt(now);
        }
        eventRepository.saveAll(running);
        return running.size();
    }

    /**
     * Фиксирует успешное завершение основной волны по кабинету.
     */
    @Transactional
    public void markMainCompleted(Long cabinetId) {
        cabinetSyncStateService.touchLastDataUpdateAt(cabinetId);
    }

    /**
     * @param excludeEventId событие, которое сейчас выполняется (RUNNING) — не учитывать при проверке «есть ли ещё main-work».
     */
    @Transactional
    public void tryFinalizeMain(Long cabinetId, Long excludeEventId) {
        boolean hasPendingMain = eventRepository.existsByCabinet_IdAndEventTypeInAndStatusInExcludingEventId(
                cabinetId,
                MAIN_EVENT_TYPES,
                WbApiEventWriter.ACTIVE_STATUSES,
                excludeEventId
        );
        if (!hasPendingMain) {
            markMainCompleted(cabinetId);
        }
    }

    /**
     * Удаляет успешно завершённые события старше {@code hours} часов.
     */
    @Transactional
    public long deleteOldSuccessfulEvents(int hours) {
        LocalDateTime threshold = LocalDateTime.now().minusHours(hours);
        return eventRepository.deleteByStatusAndFinishedAtBefore(WbApiEventStatus.SUCCESS, threshold);
    }

    /**
     * Сбрасывает событие в CREATED для немедленного повтора.
     */
    @Transactional
    public void retryNow(Long eventId) {
        WbApiEvent event = eventRepository.findById(eventId)
                .orElseThrow(() -> new IllegalArgumentException("Событие не найдено: " + eventId));
        event.setStatus(WbApiEventStatus.CREATED);
        event.setNextAttemptAt(LocalDateTime.now());
        event.setLastError(null);
        event.setFinishedAt(null);
        event.setUpdatedAt(LocalDateTime.now());
        eventRepository.save(event);
    }

    /**
     * Массовый перевод FAILED_FINAL → CREATED.
     */
    @Transactional
    public int retryAllFailedFinalNow() {
        return eventRepository.bulkRetryByStatus(
                WbApiEventStatus.FAILED_FINAL,
                WbApiEventStatus.CREATED,
                LocalDateTime.now()
        );
    }

    /**
     * Отменяет событие.
     */
    @Transactional
    public void cancel(Long eventId) {
        WbApiEvent event = eventRepository.findById(eventId)
                .orElseThrow(() -> new IllegalArgumentException("Событие не найдено: " + eventId));
        event.setStatus(WbApiEventStatus.CANCELLED);
        event.setFinishedAt(LocalDateTime.now());
        event.setUpdatedAt(LocalDateTime.now());
        eventRepository.save(event);
    }

    /**
     * Помечает событие успешным.
     */
    @Transactional
    public void markSuccess(WbApiEvent event) {
        event.setStatus(WbApiEventStatus.SUCCESS);
        event.setFinishedAt(LocalDateTime.now());
        event.setUpdatedAt(LocalDateTime.now());
        eventRepository.save(event);
    }

    /**
     * Обрабатывает ошибку выполнения: retry, defer rate-limit, {@code SKIPPED_NO_BUDGET} или финальный статус.
     */
    @Transactional
    public void markFailed(WbApiEvent event, WbApiEventExecutionResult result) {
        event.setLastError(result.errorMessage());
        event.setUpdatedAt(LocalDateTime.now());
        Long cabinetId = event.getCabinet() != null ? event.getCabinet().getId() : null;

        if (result.deferUntil() != null) {
            event.setStartedAt(null);
            if (result.countsAsAttempt()) {
                int nextAttempt = event.getAttemptCount() + 1;
                event.setAttemptCount(nextAttempt);
                if (nextAttempt >= event.getMaxAttempts()) {
                    event.setStatus(WbApiEventStatus.FAILED_FINAL);
                    event.setFinishedAt(LocalDateTime.now());
                    eventRepository.save(event);
                    logTerminalCompletion(event, cabinetId);
                    return;
                }
            }
            event.setStatus(WbApiEventStatus.DEFERRED_RATE_LIMIT);
            event.setNextAttemptAt(result.deferUntil());
            eventRepository.save(event);
            return;
        }

        if (result.terminalStatus() != null) {
            event.setAttemptCount(event.getAttemptCount() + 1);
            event.setStatus(result.terminalStatus());
            event.setFinishedAt(LocalDateTime.now());
            eventRepository.save(event);
            logTerminalCompletion(event, cabinetId);
            return;
        }

        int nextAttempt = event.getAttemptCount() + 1;
        event.setAttemptCount(nextAttempt);

        if (result.retryable() && nextAttempt < event.getMaxAttempts()) {
            event.setStatus(WbApiEventStatus.FAILED_RETRYABLE);
            event.setNextAttemptAt(LocalDateTime.now().plusSeconds(20L * nextAttempt));
            eventRepository.save(event);
            return;
        }

        event.setStatus(result.fallbackUsed() ? WbApiEventStatus.FAILED_WITH_FALLBACK : WbApiEventStatus.FAILED_FINAL);
        event.setFinishedAt(LocalDateTime.now());
        eventRepository.save(event);
        logTerminalCompletion(event, cabinetId);
    }

    private void logTerminalCompletion(WbApiEvent event, Long cabinetId) {
        if (event.getStatus() == WbApiEventStatus.SKIPPED_NO_BUDGET) {
            log.info(
                    "WB event пропущен (нет бюджета): id={}, type={}, cabinetId={}",
                    event.getId(),
                    event.getEventType(),
                    cabinetId
            );
            return;
        }
        log.warn(
                "WB event завершен с ошибкой: id={}, type={}, cabinetId={}, status={}, attempts={}/{}, error={}",
                event.getId(),
                event.getEventType(),
                cabinetId,
                event.getStatus(),
                event.getAttemptCount(),
                event.getMaxAttempts(),
                event.getLastError()
        );
    }
}
