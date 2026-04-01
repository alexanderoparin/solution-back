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
import ru.oparin.solution.model.WbApiEventType;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Component
@RequiredArgsConstructor
@Slf4j
public class WbApiEventDispatcher {

    /**
     * Не дожимать очередь подряд: у типа длинная обязательная пауза перед вызовом WB
     * ({@code wb.analytics.card-delay-ms}), иначе один поток {@code cabinet-update-*} занят десятками минут одним кабинетом.
     */
    private static final Set<WbApiEventType> SKIP_DRAIN_AFTER_SUCCESS = Set.of(
            WbApiEventType.ANALYTICS_SALES_FUNNEL_NMID
    );

    private final WbApiEventService eventService;
    private final ApplicationContext applicationContext;
    private final WbEventsProperties wbEventsProperties;
    @Qualifier("cabinetUpdateExecutor")
    private final Executor cabinetUpdateExecutor;

    @Scheduled(fixedDelayString = "${app.wb-events.poll-delay-ms:3000}")
    @SchedulerLock(name = "wbApiEventDispatcherPoll", lockAtLeastFor = "PT1S", lockAtMostFor = "PT2M")
    public void pollAndExecute() {
        int batchSize = resolveReadBatchSize();
        List<WbApiEvent> events = eventService.findDueEvents(batchSize);
        if (events.isEmpty()) {
            return;
        }
        Map<String, WbApiEvent> uniqueByCabinetAndType = new LinkedHashMap<>();
        for (WbApiEvent event : events) {
            String key = event.getCabinet().getId() + ":" + event.getEventType().name();
            uniqueByCabinetAndType.putIfAbsent(key, event);
        }

        List<WbApiEvent> selected = applyPerTypeBatchLimits(new ArrayList<>(uniqueByCabinetAndType.values()));

        List<CompletableFuture<Void>> futures = selected.stream()
                .map(event -> CompletableFuture.runAsync(() -> executeSingle(event), cabinetUpdateExecutor))
                .toList();
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
    }

    @Scheduled(fixedDelayString = "${app.wb-events.stuck-check-delay-ms:30000}")
    @SchedulerLock(name = "wbApiEventDispatcherRecoverStuck", lockAtLeastFor = "PT1S", lockAtMostFor = "PT2M")
    public void recoverStuckRunning() {
        int recovered = eventService.recoverStuckRunningEvents(wbEventsProperties.getRunningTimeoutMinutes());
        if (recovered > 0) {
            log.warn("Автовосстановление WB событий: переведено из RUNNING в retry {}", recovered);
        }
    }

    private void executeSingle(WbApiEvent event) {
        Long cabinetId = event.getCabinet() != null ? event.getCabinet().getId() : null;
        if (cabinetId != null) {
            MDC.put("cabinetTag", "[cabinet:" + cabinetId + "]");
        }
        try {
            if (!eventService.tryMarkRunning(event)) {
                return;
            }
            runMarkedEvent(event);
            drainSameCabinetTypeAfterSuccess(cabinetId, event.getEventType());
        } finally {
            MDC.remove("cabinetTag");
        }
    }

    private void runMarkedEvent(WbApiEvent event) {
        try {
            WbApiEventExecutor executor = applicationContext.getBean(event.getExecutorBeanName(), WbApiEventExecutor.class);
            WbApiEventExecutionResult result = executor.execute(event);
            if (result.success()) {
                eventService.markSuccess(event);
                return;
            }
            eventService.markFailed(event, result);
        } catch (Exception e) {
            log.error("Ошибка выполнения WB API события id={}, type={}: {}", event.getId(), event.getEventType(), e.getMessage(), e);
            eventService.markFailed(event, WbApiEventExecutionResult.retryableError(e.getMessage()));
        }
    }

    /**
     * Не ждать следующий poll: обработать следующие готовые события того же типа и кабинета подряд (лимит из конфига).
     */
    private void drainSameCabinetTypeAfterSuccess(Long cabinetId, WbApiEventType type) {
        if (cabinetId == null || type == null) {
            return;
        }
        if (SKIP_DRAIN_AFTER_SUCCESS.contains(type)) {
            return;
        }
        int max = wbEventsProperties.getMaxSameCabinetTypeDrainAfterSuccess();
        if (max <= 0) {
            return;
        }
        for (int i = 0; i < max; i++) {
            WbApiEvent next = eventService.findNextReadyEventForCabinetAndType(cabinetId, type).orElse(null);
            if (next == null) {
                return;
            }
            if (!eventService.tryMarkRunning(next)) {
                return;
            }
            runMarkedEvent(next);
        }
    }

    private int resolveReadBatchSize() {
        int sum = wbEventsProperties.getBatchSizeByType().values().stream()
                .filter(v -> v != null && v > 0)
                .mapToInt(Integer::intValue)
                .sum();
        return sum > 0 ? sum : wbEventsProperties.getDefaultBatchSize();
    }

    private List<WbApiEvent> applyPerTypeBatchLimits(List<WbApiEvent> events) {
        Map<WbApiEventType, Integer> counters = new LinkedHashMap<>();
        List<WbApiEvent> selected = new ArrayList<>();
        for (WbApiEvent event : events) {
            WbApiEventType type = event.getEventType();
            int current = counters.getOrDefault(type, 0);
            int limit = resolvePerTypeLimit(type);
            if (current >= limit) {
                continue;
            }
            counters.put(type, current + 1);
            selected.add(event);
        }
        return selected;
    }

    private int resolvePerTypeLimit(WbApiEventType type) {
        Integer fromMap = wbEventsProperties.getBatchSizeByType().get(type.name());
        if (fromMap != null && fromMap > 0) {
            return fromMap;
        }
        return wbEventsProperties.getDefaultBatchSize();
    }
}
