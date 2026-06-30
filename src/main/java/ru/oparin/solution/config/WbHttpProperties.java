package ru.oparin.solution.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * HTTP-таймауты клиентов WB API ({@link ru.oparin.solution.service.wb.AbstractWbApiClient}).
 */
@Component
@ConfigurationProperties(prefix = "wb.http")
@Data
public class WbHttpProperties {

    /**
     * Таймаут установки TCP-соединения (мс).
     */
    private int connectTimeoutMs = 15_000;

    /**
     * Таймаут ожидания тела ответа (мс). Зависшие запросы WB обрываются на этом уровне.
     */
    private int readTimeoutMs = 45_000;
}
