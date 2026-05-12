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
     * Таймаут ожидания завершения одного async-события в poll (секунды).
     */
    private int eventAwaitTimeoutSeconds;
}
