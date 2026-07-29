package ru.oparin.solution.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ru.oparin.solution.service.abtest.AbTestOrchestrator;

/**
 * Планировщик тиков А/Б-тестов (ротация, статистика, автостоп).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AbTestScheduler {

    private final AbTestOrchestrator abTestOrchestrator;

    /**
     * Каждую минуту: опрос статистики, ротация, автозавершение.
     */
    @Scheduled(cron = "15 * * * * *")
    @SchedulerLock(name = "abTestTick", lockAtLeastFor = "PT5S", lockAtMostFor = "PT4M")
    public void tick() {
        abTestOrchestrator.tick();
    }
}
