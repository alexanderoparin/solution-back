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
import ru.oparin.solution.model.WbApiEvent;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Component
@RequiredArgsConstructor
@Slf4j
public class WbApiEventDispatcher {

    private final WbApiEventService eventService;
    private final WbEventRateLimitService rateLimitService;
    private final ApplicationContext applicationContext;
    private final WbEventsProperties wbEventsProperties;
    @Qualifier("cabinetUpdateExecutor")
    private final Executor cabinetUpdateExecutor;

    @Scheduled(fixedDelayString = "${app.wb-events.poll-delay-ms}")
//    @SchedulerLock(name = "wbApiEventDispatcherPoll", lockAtLeastFor = "PT1S", lockAtMostFor = "PT2M")
    public void pollAndExecute() {
        List<WbApiEvent> events = eventService.findDueEvents();
        if (events.isEmpty()) {
            return;
        }
        log.info("WB events poll: получено событий к обработке {}", events.size());

        List<CompletableFuture<EventExecutionOutcome>> futures = events.stream()
                .map(event -> CompletableFuture.supplyAsync(() -> executeSingle(event), cabinetUpdateExecutor))
                .toList();
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        int deferredCount = 0;
        int executedCount = 0;
        int skippedCount = 0;
        for (CompletableFuture<EventExecutionOutcome> future : futures) {
            EventExecutionOutcome outcome = future.join();
            if (outcome == EventExecutionOutcome.DEFERRED_RATE_LIMIT) {
                deferredCount++;
            } else if (outcome == EventExecutionOutcome.EXECUTED) {
                executedCount++;
            } else if (outcome == EventExecutionOutcome.SKIPPED) {
                skippedCount++;
            }
        }
        log.info(
                "WB events poll: выполнено {}, отложено по rate-limit {}, пропущено {} (всего выбрано {})",
                executedCount, deferredCount, skippedCount, events.size()
        );
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
            if (result.success()) {
                eventService.markSuccess(event);
                return EventExecutionOutcome.EXECUTED;
            }
            eventService.markFailed(event, result);
            return EventExecutionOutcome.EXECUTED;
        } catch (Exception e) {
            log.error("Ошибка выполнения WB API события id={}, type={}: {}", event.getId(), event.getEventType(), e.getMessage(), e);
            eventService.markFailed(event, WbApiEventExecutionResult.retryableError(e.getMessage()));
            return EventExecutionOutcome.EXECUTED;
        } finally {
            MDC.remove("cabinetTag");
        }
    }

    private enum EventExecutionOutcome {
        EXECUTED,
        DEFERRED_RATE_LIMIT,
        SKIPPED
    }

}
