package ru.oparin.solution.service.events;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;
import ru.oparin.solution.config.WbEventsProperties;
import ru.oparin.solution.exception.WbRateLimitDeferException;
import ru.oparin.solution.model.WbApiEvent;
import ru.oparin.solution.model.WbApiEventType;
import ru.oparin.solution.repository.ProductCardRepository;
import ru.oparin.solution.service.events.payload.AnalyticsSalesFunnelPayload;
import ru.oparin.solution.service.events.payload.StocksByNmIdPayload;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
@RequiredArgsConstructor
@Slf4j
public class WbApiEventDispatcher {

    private final WbApiEventService eventService;
    private final WbEventRateLimitService rateLimitService;
    private final ApplicationContext applicationContext;
    private final WbEventsProperties wbEventsProperties;
    private final ProductCardRepository productCardRepository;
    @Qualifier("cabinetUpdateExecutor")
    private final ThreadPoolTaskExecutor cabinetUpdateExecutor;

    /**
     * Не Spring-bean: иначе {@code @Scheduled} начнёт выполняться в этом однопоточном планировщике.
     */
    private ScheduledExecutorService executionTimeoutScheduler;

    /** Поток, выполняющий {@link #executeRunningEvent}; нужен для interrupt по таймауту. */
    private final ConcurrentMap<Long, Thread> runningThreadsByEventId = new ConcurrentHashMap<>();
    /** Событие уже переведено в retry по таймауту выполнения. */
    private final ConcurrentMap<Long, AtomicBoolean> executionTimedOutByEventId = new ConcurrentHashMap<>();

    @PostConstruct
    void initExecutionTimeoutScheduler() {
        executionTimeoutScheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "wb-event-exec-timeout");
            thread.setDaemon(true);
            return thread;
        });
    }

    @PreDestroy
    void shutdownExecutionTimeoutScheduler() {
        if (executionTimeoutScheduler != null) {
            executionTimeoutScheduler.shutdownNow();
        }
    }

    @Scheduled(fixedDelayString = "${app.wb-events.poll-delay-ms}")
    @SchedulerLock(name = "wbApiEventDispatcherPoll", lockAtLeastFor = "PT1S", lockAtMostFor = "PT1M")
    public void pollAndExecute() {
        List<WbApiEvent> events = eventService.findDueEvents();
        if (events.isEmpty()) {
            return;
        }
        events = prioritizeEventsForPriorityCards(events);
        log.info("WB events poll: получено событий к обработке {}", events.size());

        GroupedExecutionPlan executionPlan = buildGroupedExecutionPlan(events);
        logExecutionPlanAndPool("старт poll", executionPlan);

        int deferredCount = 0;
        int executedCount = 0;
        int skippedCount = 0;
        int timeoutCount = 0;
        int queueTotal = executionPlan.futures().size();
        List<CompletableFuture<EventExecutionOutcome>> futures = executionPlan.futures();
        for (int i = 0; i < queueTotal; i++) {
            CompletableFuture<EventExecutionOutcome> future = futures.get(i);
            WbApiEvent event = events.get(i);
            EventExecutionOutcome outcome = awaitOutcome(future, event, i, queueTotal);
            if (outcome == EventExecutionOutcome.TIMEOUT) {
                timeoutCount++;
                continue;
            }
            if (outcome == EventExecutionOutcome.DEFERRED_RATE_LIMIT) {
                deferredCount++;
            } else if (outcome == EventExecutionOutcome.EXECUTED) {
                executedCount++;
            } else if (outcome == EventExecutionOutcome.SKIPPED) {
                skippedCount++;
            }
        }
        log.info(
                "WB events poll: выполнено {}, отложено по rate-limit {}, пропущено {}, таймаут {} (всего выбрано {})",
                executedCount, deferredCount, skippedCount, timeoutCount, events.size()
        );
        logExecutorPoolState("конец poll");
    }

    private void logExecutionPlanAndPool(String phase, GroupedExecutionPlan plan) {
        log.info(
                "WB events poll: {} — план: событий {}, цепочек (cabinet+type) {}, сразу отправлено в пул {}, "
                        + "ожидают цепочку (ещё не в пуле) {}, {}",
                phase,
                plan.futures().size(),
                plan.executionGroupCount(),
                plan.immediatePoolSubmits(),
                plan.chainedPendingSubmits(),
                formatExecutorPoolState()
        );
    }

    private void logExecutorPoolState(String phase) {
        log.info("WB events poll: {} — пул cabinet-update: {}", phase, formatExecutorPoolState());
    }

    private String formatExecutorPoolState() {
        ThreadPoolExecutor threadPool = cabinetUpdateExecutor.getThreadPoolExecutor();
        if (threadPool == null) {
            return "недоступен";
        }
        return String.format(
                "active=%d, pool=%d, core=%d, max=%d, queue=%d, completed=%d",
                threadPool.getActiveCount(),
                threadPool.getPoolSize(),
                threadPool.getCorePoolSize(),
                threadPool.getMaximumPoolSize(),
                threadPool.getQueue().size(),
                threadPool.getCompletedTaskCount()
        );
    }

    /**
     * План async-выполнения: futures по порядку poll и счётчики постановки в {@code cabinetUpdateExecutor}.
     *
     * @param executionGroupCount     число уникальных пар (cabinetId, eventType)
     * @param immediatePoolSubmits    задач сразу отправлено в пул (голова цепочки)
     * @param chainedPendingSubmits   событий в цепочке, ещё не отправленных в пул
     */
    private record GroupedExecutionPlan(
            List<CompletableFuture<EventExecutionOutcome>> futures,
            int executionGroupCount,
            int immediatePoolSubmits,
            int chainedPendingSubmits
    ) {
    }

    /**
     * Формирует план выполнения событий:
     * <ul>
     *     <li>между разными (cabinetId, eventType) — параллельно;</li>
     *     <li>внутри одной пары (cabinetId, eventType) — строго последовательно.</li>
     * </ul>
     * Это сохраняет throughput, но устраняет «перемешивание» порядка внутри одной группы.
     */
    private GroupedExecutionPlan buildGroupedExecutionPlan(List<WbApiEvent> events) {
        Map<String, CompletableFuture<EventExecutionOutcome>> tailsByGroup = new HashMap<>();
        List<CompletableFuture<EventExecutionOutcome>> plan = new ArrayList<>(events.size());
        int immediatePoolSubmits = 0;

        for (WbApiEvent event : events) {
            String groupKey = executionGroupKey(event);
            CompletableFuture<EventExecutionOutcome> prevTail = tailsByGroup.get(groupKey);
            CompletableFuture<EventExecutionOutcome> nextFuture;
            if (prevTail == null) {
                immediatePoolSubmits++;
                nextFuture = CompletableFuture.supplyAsync(() -> executeSingle(event), cabinetUpdateExecutor);
            } else {
                // Продолжаем цепочку даже если предыдущий future завершился с исключением/отменой.
                nextFuture = prevTail
                        .handle((ignored, ex) -> null)
                        .thenCompose(ignored -> CompletableFuture.supplyAsync(() -> executeSingle(event), cabinetUpdateExecutor));
            }
            tailsByGroup.put(groupKey, nextFuture);
            plan.add(nextFuture);
        }
        int executionGroupCount = tailsByGroup.size();
        int chainedPendingSubmits = events.size() - immediatePoolSubmits;
        return new GroupedExecutionPlan(plan, executionGroupCount, immediatePoolSubmits, chainedPendingSubmits);
    }

    private String executionGroupKey(WbApiEvent event) {
        Long cabinetId = event.getCabinet() != null ? event.getCabinet().getId() : null;
        String eventType = event.getEventType() != null ? event.getEventType().name() : "UNKNOWN";
        return (cabinetId != null ? cabinetId : -1L) + "|" + eventType;
    }

    /**
     * Ожидает завершения async-задачи без лимита по времени.
     * Таймаут выполнения считается с {@code tryMarkRunning} внутри {@link #executeSingle}.
     */
    private EventExecutionOutcome awaitOutcome(
            CompletableFuture<EventExecutionOutcome> future,
            WbApiEvent event,
            int queueIndex,
            int queueTotal
    ) {
        String eventLabel = formatEventForLog(event, queueIndex, queueTotal);
        try {
            return future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("WB events poll: поток прерван при ожидании async-события: {}", eventLabel);
            future.cancel(true);
            return EventExecutionOutcome.INTERRUPTED;
        } catch (ExecutionException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            log.error("WB events poll: ошибка ожидания async-события ({}): {}", eventLabel, cause.getMessage(), cause);
            return EventExecutionOutcome.INTERRUPTED;
        } catch (CancellationException e) {
            log.warn("WB events poll: async-событие отменено (CancellationException): {}", eventLabel);
            return EventExecutionOutcome.INTERRUPTED;
        }
    }

    private String formatEventForLog(WbApiEvent event, int queueIndex, int queueTotal) {
        Long cabinetId = event.getCabinet() != null ? event.getCabinet().getId() : null;
        Long nmId = extractNmId(event);
        StringBuilder label = new StringBuilder();
        label.append("id=").append(event.getId());
        label.append(", type=").append(event.getEventType());
        if (cabinetId != null) {
            label.append(", cabinetId=").append(cabinetId);
        }
        if (nmId != null) {
            label.append(", nmId=").append(nmId);
        }
        if (event.getDedupKey() != null) {
            label.append(", dedupKey=").append(event.getDedupKey());
        }
        if (queueIndex >= 0 && queueTotal > 0) {
            label.append(", позиция=").append(queueIndex + 1).append("/").append(queueTotal);
        }
        if (event.getStatus() != null) {
            label.append(", status=").append(event.getStatus());
        }
        return label.toString();
    }

    private List<WbApiEvent> prioritizeEventsForPriorityCards(List<WbApiEvent> events) {
        Map<Long, Set<Long>> nmIdsByCabinet = collectNmIdsByCabinet(events);
        if (nmIdsByCabinet.isEmpty()) {
            return events;
        }

        Map<Long, Set<Long>> priorityNmIdsByCabinet = new HashMap<>();
        nmIdsByCabinet.forEach((cabinetId, nmIds) -> {
            List<Long> priorityNmIds = productCardRepository.findPriorityNmIdsByCabinetAndNmIdIn(cabinetId, nmIds.stream().toList());
            priorityNmIdsByCabinet.put(cabinetId, new HashSet<>(priorityNmIds));
        });

        // Сначала события по приоритетным карточкам; среди равных по этому признаку — меньший id раньше (FIFO по очереди постановки).
        return events.stream()
                .sorted(
                        Comparator.comparing((WbApiEvent event) -> isPriorityNmEvent(event, priorityNmIdsByCabinet))
                                .reversed()
                                .thenComparing(WbApiEvent::getId, Comparator.nullsLast(Comparator.naturalOrder()))
                )
                .toList();
    }

    private Map<Long, Set<Long>> collectNmIdsByCabinet(List<WbApiEvent> events) {
        Map<Long, Set<Long>> result = new HashMap<>();
        for (WbApiEvent event : events) {
            Long cabinetId = event.getCabinet() != null ? event.getCabinet().getId() : null;
            Long nmId = extractNmId(event);
            if (cabinetId == null || nmId == null) {
                continue;
            }
            result.computeIfAbsent(cabinetId, ignored -> new HashSet<>()).add(nmId);
        }
        return result;
    }

    private boolean isPriorityNmEvent(WbApiEvent event, Map<Long, Set<Long>> priorityNmIdsByCabinet) {
        Long cabinetId = event.getCabinet() != null ? event.getCabinet().getId() : null;
        Long nmId = extractNmId(event);
        if (cabinetId == null || nmId == null) {
            return false;
        }
        return priorityNmIdsByCabinet.getOrDefault(cabinetId, Set.of()).contains(nmId);
    }

    private Long extractNmId(WbApiEvent event) {
        try {
            if (event.getEventType() == WbApiEventType.ANALYTICS_SALES_FUNNEL_NMID) {
                AnalyticsSalesFunnelPayload payload = eventService.readPayload(event, AnalyticsSalesFunnelPayload.class);
                return payload.nmId();
            }
            if (event.getEventType() == WbApiEventType.STOCKS_BY_NMID) {
                StocksByNmIdPayload payload = eventService.readPayload(event, StocksByNmIdPayload.class);
                return payload.nmId();
            }
        } catch (Exception e) {
            log.warn("Не удалось извлечь nmId из payload события id={}, type={}: {}",
                    event.getId(), event.getEventType(), e.getMessage());
        }
        return null;
    }

    @Scheduled(fixedDelayString = "${app.wb-events.stuck-check-delay-ms}")
    @SchedulerLock(name = "wbApiEventDispatcherRecoverStuck", lockAtLeastFor = "PT1S", lockAtMostFor = "PT2M")
    public void recoverStuckRunning() {
        int recovered = eventService.recoverStuckRunningEvents(wbEventsProperties.getRunningTimeoutMinutes());
        if (recovered > 0) {
            log.warn("Автовосстановление WB событий: переведено из RUNNING в retry {}", recovered);
        }
    }

    private EventExecutionOutcome executeSingle(WbApiEvent event) {
        Long cabinetId = event.getCabinet() != null ? event.getCabinet().getId() : null;
        if (cabinetId != null) {
            MDC.put("cabinetTag", "[cabinet:" + cabinetId + "]");
        }
        ScheduledFuture<?> timeoutTask = null;
        try {
            if (!eventService.tryMarkRunning(event)) {
                return EventExecutionOutcome.SKIPPED;
            }
            timeoutTask = scheduleExecutionTimeout(event);
            return executeRunningEvent(event);
        } finally {
            clearExecutionTimeoutState(event.getId(), timeoutTask);
            MDC.remove("cabinetTag");
        }
    }

    private EventExecutionOutcome executeRunningEvent(WbApiEvent event) {
        if (isExecutionTimedOut(event.getId())) {
            return EventExecutionOutcome.TIMEOUT;
        }
        try {
            LocalDateTime deferUntil = rateLimitService.acquireOrDefer(event);
            if (deferUntil != null) {
                eventService.markFailed(
                        event,
                        WbApiEventExecutionResult.deferredRateLimit(
                                "Rate limit по кабинету и endpoint: отложено до " + deferUntil,
                                deferUntil
                        )
                );
                return resolveOutcomeAfterRunning(event.getId(), EventExecutionOutcome.DEFERRED_RATE_LIMIT);
            }
            WbApiEventExecutor executor = applicationContext.getBean(event.getExecutorBeanName(), WbApiEventExecutor.class);
            WbApiEventExecutionResult result = executor.execute(event);
            long eventId = event.getId();
            if (result.success()) {
                eventService.markSuccessIfRunning(eventId);
                return resolveOutcomeAfterRunning(eventId, EventExecutionOutcome.EXECUTED);
            }
            if (result.deferUntil() != null) {
                eventService.markFailed(event, result);
                return resolveOutcomeAfterRunning(eventId, EventExecutionOutcome.DEFERRED_RATE_LIMIT);
            }
            eventService.markFailedIfRunning(eventId, result);
            return resolveOutcomeAfterRunning(eventId, EventExecutionOutcome.EXECUTED);
        } catch (WbRateLimitDeferException e) {
            eventService.markFailed(
                    event,
                    WbApiEventExecutionResult.deferredRateLimit(e.getMessage(), e.getDeferUntil())
            );
            return resolveOutcomeAfterRunning(event.getId(), EventExecutionOutcome.DEFERRED_RATE_LIMIT);
        } catch (Exception e) {
            WbRateLimitDeferException defer = WbRateLimitDeferException.findInChain(e);
            if (defer != null) {
                eventService.markFailed(
                        event,
                        WbApiEventExecutionResult.deferredRateLimit(defer.getMessage(), defer.getDeferUntil())
                );
                return resolveOutcomeAfterRunning(event.getId(), EventExecutionOutcome.DEFERRED_RATE_LIMIT);
            }
            log.error("Ошибка выполнения WB API события id={}, type={}: {}", event.getId(), event.getEventType(), e.getMessage(), e);
            eventService.markFailedIfRunning(event.getId(), WbApiEventExecutionResult.retryableError(e.getMessage()));
            return resolveOutcomeAfterRunning(event.getId(), EventExecutionOutcome.EXECUTED);
        }
    }

    private EventExecutionOutcome resolveOutcomeAfterRunning(long eventId, EventExecutionOutcome outcome) {
        if (isExecutionTimedOut(eventId)) {
            return EventExecutionOutcome.TIMEOUT;
        }
        return outcome;
    }

    private ScheduledFuture<?> scheduleExecutionTimeout(WbApiEvent event) {
        int timeoutSeconds = Math.max(1, wbEventsProperties.getEventAwaitTimeoutSeconds());
        long eventId = event.getId();
        runningThreadsByEventId.put(eventId, Thread.currentThread());
        return executionTimeoutScheduler.schedule(
                () -> handleExecutionTimeout(event, timeoutSeconds),
                timeoutSeconds,
                TimeUnit.SECONDS
        );
    }

    private void handleExecutionTimeout(WbApiEvent event, int timeoutSeconds) {
        long eventId = event.getId();
        AtomicBoolean timedOut = executionTimedOutByEventId.computeIfAbsent(eventId, ignored -> new AtomicBoolean());
        if (!timedOut.compareAndSet(false, true)) {
            return;
        }
        Thread runningThread = runningThreadsByEventId.get(eventId);
        if (runningThread != null) {
            runningThread.interrupt();
        }
        boolean reverted = eventService.revertRunningAfterExecutionTimeout(eventId, timeoutSeconds);
        if (reverted) {
            log.error(
                    "WB event: таймаут выполнения (>{}с) с момента старта: {}",
                    timeoutSeconds,
                    formatEventForLog(event, -1, -1)
            );
        }
    }

    private void clearExecutionTimeoutState(long eventId, ScheduledFuture<?> timeoutTask) {
        runningThreadsByEventId.remove(eventId);
        executionTimedOutByEventId.remove(eventId);
        if (timeoutTask != null) {
            timeoutTask.cancel(false);
        }
    }

    private boolean isExecutionTimedOut(long eventId) {
        AtomicBoolean timedOut = executionTimedOutByEventId.get(eventId);
        return timedOut != null && timedOut.get();
    }

    private enum EventExecutionOutcome {
        EXECUTED,
        DEFERRED_RATE_LIMIT,
        SKIPPED,
        TIMEOUT,
        INTERRUPTED
    }

}
