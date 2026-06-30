package ru.oparin.solution.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Настройки диспетчера WB API событий (планировщика {@code WbApiEventDispatcher}).
 */
@Component
@ConfigurationProperties(prefix = "app.wb-events")
@Data
public class WbEventsProperties {

    /**
     * Интервал вычитки событий (мс).
     */
    private long pollDelayMs;

    /**
     * Интервал проверки "зависших" RUNNING (мс).
     */
    private long stuckCheckDelayMs;

    /**
     * Таймаут RUNNING события в минутах.
     */
    private int runningTimeoutMinutes;

    /**
     * Таймаут выполнения одного события с момента {@code tryMarkRunning} (секунды).
     * Не относится к ожиданию в очереди poll или пула потоков.
     */
    private int eventAwaitTimeoutSeconds;
}
