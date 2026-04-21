package ru.oparin.solution.service.events;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.annotation.Scheduled;
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
    private final Executor cabinetUpdateExecutor;

    @Scheduled(fixedDelayString = "${app.wb-events.poll-delay-ms}")
    @SchedulerLock(name = "wbApiEventDispatcherPoll", lockAtLeastFor = "PT1S", lockAtMostFor = "PT1M")
    public void pollAndExecute() {
        List<WbApiEvent> events = eventService.findDueEvents();
        if (events.isEmpty()) {
            return;
        }
        events = prioritizeEventsForPriorityCards(events);
        log.info("WB events poll: получено событий к обработке {}", events.size());

        List<CompletableFuture<EventExecutionOutcome>> futures = events.stream()
                .map(event -> CompletableFuture.supplyAsync(() -> executeSingle(event), cabinetUpdateExecutor))
                .toList();

        int timeoutSeconds = Math.max(1, wbEventsProperties.getEventAwaitTimeoutSeconds());
        int deferredCount = 0;
        int executedCount = 0;
        int skippedCount = 0;
        int timeoutCount = 0;
        for (int i = 0; i < futures.size(); i++) {
            CompletableFuture<EventExecutionOutcome> future = futures.get(i);
            WbApiEvent event = events.get(i);
            EventExecutionOutcome outcome = awaitOutcome(future, event.getId(), timeoutSeconds);
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
    }

    private EventExecutionOutcome awaitOutcome(CompletableFuture<EventExecutionOutcome> future, Long eventId, int timeoutSeconds) {
        try {
            return future.get(timeoutSeconds, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            log.error("WB events poll: таймаут ожидания async-события (>{}с), future отменен", timeoutSeconds);
            revertRunningAfterPollAwaitTimeout(eventId, timeoutSeconds);
            return EventExecutionOutcome.TIMEOUT;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("WB events poll: поток прерван при ожидании async-события");
            future.cancel(true);
            revertRunningAfterPollAwaitTimeout(eventId, timeoutSeconds);
            return EventExecutionOutcome.TIMEOUT;
        } catch (ExecutionException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            log.error("WB events poll: ошибка ожидания async-события: {}", cause.getMessage(), cause);
            future.cancel(true);
            revertRunningAfterPollAwaitTimeout(eventId, timeoutSeconds);
            return EventExecutionOutcome.TIMEOUT;
        } catch (CancellationException e) {
            log.error("WB events poll: async-событие отменено (CancellationException)");
            revertRunningAfterPollAwaitTimeout(eventId, timeoutSeconds);
            return EventExecutionOutcome.TIMEOUT;
        }
    }

    /**
     * Если async-задача не вернулась за poll-таймаут, событие остаётся RUNNING в БД — переводим в retry.
     */
    private void revertRunningAfterPollAwaitTimeout(Long eventId, int timeoutSeconds) {
        if (eventId == null) {
            return;
        }
        eventService.revertRunningAfterAsyncPollTimeout(eventId, timeoutSeconds);
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
        try {
            if (!eventService.tryMarkRunning(event)) {
                return EventExecutionOutcome.SKIPPED;
            }
            LocalDateTime deferUntil = rateLimitService.acquireOrDefer(event);
            if (deferUntil != null) {
                eventService.markFailed(
                        event,
                        WbApiEventExecutionResult.deferredRateLimit(
                                "Rate limit по кабинету и endpoint: отложено до " + deferUntil,
                                deferUntil
                        )
                );
                return EventExecutionOutcome.DEFERRED_RATE_LIMIT;
            }
            WbApiEventExecutor executor = applicationContext.getBean(event.getExecutorBeanName(), WbApiEventExecutor.class);
            WbApiEventExecutionResult result = executor.execute(event);
            long eventId = event.getId();
            if (result.success()) {
                eventService.markSuccessIfRunning(eventId);
                return EventExecutionOutcome.EXECUTED;
            }
            if (result.deferUntil() != null) {
                eventService.markFailed(event, result);
                return EventExecutionOutcome.DEFERRED_RATE_LIMIT;
            }
            eventService.markFailedIfRunning(eventId, result);
            return EventExecutionOutcome.EXECUTED;
        } catch (WbRateLimitDeferException e) {
            eventService.markFailed(
                    event,
                    WbApiEventExecutionResult.deferredRateLimit(e.getMessage(), e.getDeferUntil())
            );
            return EventExecutionOutcome.DEFERRED_RATE_LIMIT;
        } catch (Exception e) {
            WbRateLimitDeferException defer = WbRateLimitDeferException.findInChain(e);
            if (defer != null) {
                eventService.markFailed(
                        event,
                        WbApiEventExecutionResult.deferredRateLimit(defer.getMessage(), defer.getDeferUntil())
                );
                return EventExecutionOutcome.DEFERRED_RATE_LIMIT;
            }
            log.error("Ошибка выполнения WB API события id={}, type={}: {}", event.getId(), event.getEventType(), e.getMessage(), e);
            eventService.markFailedIfRunning(event.getId(), WbApiEventExecutionResult.retryableError(e.getMessage()));
            return EventExecutionOutcome.EXECUTED;
        } finally {
            MDC.remove("cabinetTag");
        }
    }

    private enum EventExecutionOutcome {
        EXECUTED,
        DEFERRED_RATE_LIMIT,
        SKIPPED,
        TIMEOUT
    }

}
