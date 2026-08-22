package ru.oparin.solution.service.events;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;
import ru.oparin.solution.config.OzonEventsProperties;
import ru.oparin.solution.model.OzonApiEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Component
@RequiredArgsConstructor
@Slf4j
public class OzonApiEventDispatcher {

    private final OzonApiEventService eventService;
    private final ApplicationContext applicationContext;
    private final OzonEventsProperties ozonEventsProperties;
    @Qualifier("cabinetUpdateExecutor")
    private final ThreadPoolTaskExecutor cabinetUpdateExecutor;

    @Scheduled(fixedDelayString = "${app.ozon-events.poll-delay-ms:5500}")
    @SchedulerLock(name = "ozonApiEventDispatcherPoll", lockAtLeastFor = "PT1S", lockAtMostFor = "PT1M")
    public void pollAndExecute() {
        List<OzonApiEvent> events = eventService.findDueEvents();
        if (events.isEmpty()) {
            return;
        }
        log.info("Ozon events poll: получено событий к обработке {}", events.size());
        int executed = 0;
        int skipped = 0;
        List<CompletableFuture<Boolean>> futures = new ArrayList<>(events.size());
        for (OzonApiEvent event : events) {
            futures.add(CompletableFuture.supplyAsync(() -> executeSingle(event), cabinetUpdateExecutor));
        }
        int awaitSeconds = Math.max(1, ozonEventsProperties.getEventAwaitTimeoutSeconds());
        for (int i = 0; i < futures.size(); i++) {
            try {
                if (Boolean.TRUE.equals(futures.get(i).get(awaitSeconds, TimeUnit.SECONDS))) {
                    executed++;
                } else {
                    skipped++;
                }
            } catch (TimeoutException e) {
                log.warn("Ozon events poll: таймаут ожидания события id={}", events.get(i).getId());
                futures.get(i).cancel(true);
            } catch (Exception e) {
                log.error("Ozon events poll: ошибка ожидания события id={}: {}", events.get(i).getId(), e.getMessage());
            }
        }
        log.info("Ozon events poll: выполнено {}, пропущено {} (всего {})", executed, skipped, events.size());
    }

    @Scheduled(fixedDelayString = "${app.ozon-events.stuck-check-delay-ms:16000}")
    @SchedulerLock(name = "ozonApiEventDispatcherRecoverStuck", lockAtLeastFor = "PT1S", lockAtMostFor = "PT2M")
    public void recoverStuckRunning() {
        int recovered = eventService.recoverStuckRunningEvents(ozonEventsProperties.getRunningTimeoutMinutes());
        if (recovered > 0) {
            log.warn("Автовосстановление Ozon событий: переведено из RUNNING в retry {}", recovered);
        }
    }

    private boolean executeSingle(OzonApiEvent event) {
        Long cabinetId = event.getCabinet() != null ? event.getCabinet().getId() : null;
        if (cabinetId != null) {
            MDC.put("cabinetTag", "[cabinet:" + cabinetId + "]");
        }
        try {
            if (!eventService.tryMarkRunning(event)) {
                return false;
            }
            OzonApiEventExecutor executor = applicationContext.getBean(
                    event.getExecutorBeanName(), OzonApiEventExecutor.class);
            OzonApiEventExecutionResult result = executor.execute(event);
            long eventId = event.getId();
            if (result.success()) {
                eventService.markSuccessIfRunning(eventId);
                return true;
            }
            eventService.markFailedIfRunning(eventId, result);
            return true;
        } catch (Exception e) {
            log.error("Ozon API событие id={}, type={}: {}", event.getId(), event.getEventType(), e.getMessage(), e);
            eventService.markFailedIfRunning(event.getId(), OzonApiEventExecutionResult.retryableError(e.getMessage()));
            return true;
        } finally {
            MDC.remove("cabinetTag");
        }
    }
}
