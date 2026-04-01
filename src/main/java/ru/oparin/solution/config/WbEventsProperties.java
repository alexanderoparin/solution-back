package ru.oparin.solution.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
@ConfigurationProperties(prefix = "app.wb-events")
@Data
public class WbEventsProperties {

    /**
     * Интервал вычитки событий (мс).
     */
    private long pollDelayMs = 2000;

    /**
     * Интервал проверки "зависших" RUNNING (мс).
     */
    private long stuckCheckDelayMs = 30000;

    /**
     * Таймаут RUNNING события в минутах.
     */
    private int runningTimeoutMinutes = 15;

    /**
     * Базовый лимит вычитки на тип, если тип не задан в map.
     */
    private int defaultBatchSize = 50;

    /**
     * Лимит событий данного типа за один poll: отдельный SQL-запрос на тип + верхняя граница после дедупа (кабинет+тип).
     */
    private Map<String, Integer> batchSizeByType = new HashMap<>();
}
