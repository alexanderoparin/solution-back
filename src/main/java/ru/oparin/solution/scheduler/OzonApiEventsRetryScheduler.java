package ru.oparin.solution.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ru.oparin.solution.service.events.OzonApiEventService;

/**
 * Планировщик повторного запуска финально-ошибочных Ozon API событий.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OzonApiEventsRetryScheduler {

    private final OzonApiEventService ozonApiEventService;

    @Scheduled(cron = "0 0 */6 * * ?")
    @SchedulerLock(name = "ozonApiEventsRetryFailedFinalHourly", lockAtLeastFor = "PT5S", lockAtMostFor = "PT10M")
    @Transactional
    public void retryFailedFinalEventsHourly() {
        int updated = ozonApiEventService.retryAllFailedFinalNow();
        if (updated > 0) {
            log.info("Авто-retry Ozon API событий: переведено из FAILED_FINAL в CREATED: {}", updated);
        }
    }
}
