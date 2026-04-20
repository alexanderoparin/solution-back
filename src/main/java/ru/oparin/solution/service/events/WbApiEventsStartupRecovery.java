package ru.oparin.solution.service.events;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Сбрасывает WB API события, оставшиеся в RUNNING при аварийной остановке / рестарте сервера,
 * чтобы диспетчер не бесконечно «пропускал» их из‑за {@code tryMarkRunning}.
 */
@Component
@Order(0)
@RequiredArgsConstructor
@Slf4j
public class WbApiEventsStartupRecovery implements ApplicationRunner {

    private final WbApiEventService wbApiEventService;

    @Override
    public void run(ApplicationArguments args) {
        int n = wbApiEventService.recoverRunningEventsAfterJvmStop();
        if (n > 0) {
            log.warn("WB API события: после старта JVM сброшено {} записей из RUNNING в повтор", n);
        }
    }
}
