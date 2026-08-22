package ru.oparin.solution.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Настройки диспетчера Ozon API событий.
 */
@Component
@ConfigurationProperties(prefix = "app.ozon-events")
@Data
public class OzonEventsProperties {

    private long pollDelayMs = 5500;
    private long stuckCheckDelayMs = 16000;
    private int runningTimeoutMinutes = 15;
    private int eventAwaitTimeoutSeconds = 60;
    private int pollBatchSize = 50;
}
