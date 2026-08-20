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
     * Должен быть чуть больше {@code wb.http.read-timeout-ms} (запас на rate-limit defer без HTTP).
     */
    private int eventAwaitTimeoutSeconds;

    /**
     * Максимум due-событий за один poll. Без лимита при большой очереди poll монopolizирует
     * пул БД и планировщик, HTTP-запросы начинают висеть в ожидании соединений.
     */
    private int pollBatchSize;
}
