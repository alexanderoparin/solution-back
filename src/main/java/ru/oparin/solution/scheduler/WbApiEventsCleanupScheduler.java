package ru.oparin.solution.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ru.oparin.solution.service.events.WbApiEventService;

@Component
@RequiredArgsConstructor
@Slf4j
public class WbApiEventsCleanupScheduler {

    private final WbApiEventService wbApiEventService;

    /**
     * Удаляет успешно выполненные события старше 12 часов
     */
    @Scheduled(cron = "0 0 0,12 * * *")
    @SchedulerLock(name = "wbApiEventsCleanupWeekly", lockAtLeastFor = "PT5S", lockAtMostFor = "PT10M")
    @Transactional
    public void cleanupOldSuccessfulEvents() {
        int hours = 12;
        long deleted = wbApiEventService.deleteOldSuccessfulEvents(hours);
        if (deleted > 0) {
            log.info("Очистка WB API событий: удалено успешно выполненных старше {} часов: {}", hours, deleted);
        }
    }
}
