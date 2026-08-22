package ru.oparin.solution.service.events;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Сбрасывает Ozon API события, оставшиеся в RUNNING при рестарте сервера.
 */
@Component
@Order(0)
@RequiredArgsConstructor
@Slf4j
public class OzonApiEventsStartupRecovery implements ApplicationRunner {

    private final OzonApiEventService ozonApiEventService;

    @Override
    public void run(ApplicationArguments args) {
        int n = ozonApiEventService.recoverRunningEventsAfterJvmStop();
        if (n > 0) {
            log.warn("Ozon API события: после старта JVM сброшено {} записей из RUNNING в повтор", n);
        }
    }
}
