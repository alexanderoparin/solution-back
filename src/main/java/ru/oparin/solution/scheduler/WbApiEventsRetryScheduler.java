package ru.oparin.solution.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ru.oparin.solution.service.events.WbApiEventService;

/**
 * Планировщик повторного запуска финально-ошибочных WB API событий.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class WbApiEventsRetryScheduler {

    private final WbApiEventService wbApiEventService;

    /**
     * Переводит события в статусе FAILED_FINAL обратно в CREATED.
     */
    @Scheduled(cron = "0 0 */6 * * ?")
    @SchedulerLock(name = "wbApiEventsRetryFailedFinalHourly", lockAtLeastFor = "PT5S", lockAtMostFor = "PT10M")
    @Transactional
    public void retryFailedFinalEventsHourly() {
        int updated = wbApiEventService.retryAllFailedFinalNow();
        if (updated > 0) {
            log.info("Авто-retry WB API событий: переведено из FAILED_FINAL в CREATED: {}", updated);
        }
    }
}
