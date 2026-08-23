package ru.oparin.solution.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.net.URI;

/**
 * Базовые URL Ozon API (Seller и Performance). Менять хост только здесь.
 */
@Getter
@AllArgsConstructor
public enum OzonApiBaseUrl {
    /** Основной Seller API (каталог, цены, остатки, seller/info). */
    SELLER("https://api-seller.ozon.ru"),
    /** Performance API (реклама, OAuth client_credentials). */
    PERFORMANCE("https://api-performance.ozon.ru");

    private final String defaultBaseUrl;

    /**
     * Имя хоста без схемы (для логов).
     */
    public String getHost() {
        return URI.create(defaultBaseUrl).getHost();
    }
}
