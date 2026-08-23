package ru.oparin.solution.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Типы событий Ozon Seller API с привязкой к endpoint.
 * Используется как единый источник метаданных для очереди и HTTP-клиентов.
 */
@Getter
@RequiredArgsConstructor
public enum OzonApiEventType {
    /** Постраничная загрузка каталога товаров. */
    PRODUCT_LIST_PAGE(
            OzonApiBaseUrl.SELLER,
            "/v3/product/list"),
    /**
     * Детальная информация по product_id.
     * Вызывается из {@code OzonProductListPageEventExecutor}, отдельно в очередь не ставится.
     */
    PRODUCT_INFO_LIST(
            OzonApiBaseUrl.SELLER,
            "/v3/product/info/list"),
    /** Загрузка цен по кабинету (все страницы). */
    PRICES_CABINET(
            OzonApiBaseUrl.SELLER,
            "/v5/product/info/prices"),
    /** Загрузка остатков по кабинету. */
    STOCKS_CABINET(
            OzonApiBaseUrl.SELLER,
            "/v4/product/info/stocks"),
    /** Ежедневная аналитика продаж (базовые метрики). */
    ANALYTICS_DATA_CABINET(
            OzonApiBaseUrl.SELLER,
            "/v1/analytics/data"),
    /** Проверка учётных данных продавца (seller/info). */
    SELLER_INFO(
            OzonApiBaseUrl.SELLER,
            "/v1/seller/info"),
    /** OAuth-токен Performance API (POST /api/client/token). В очередь не ставится. */
    PERFORMANCE_TOKEN(
            OzonApiBaseUrl.PERFORMANCE,
            "/api/client/token"),
    /** Загрузка списка рекламных кампаний Performance API. */
    CAMPAIGNS_CABINET(
            OzonApiBaseUrl.PERFORMANCE,
            "/api/client/campaign"),
    /** Дневная статистика РК Performance API. */
    CAMPAIGN_STATS_CABINET(
            OzonApiBaseUrl.PERFORMANCE,
            "/api/client/statistics/daily/json");

    /** Базовый URL группы Ozon API для данного типа. */
    private final OzonApiBaseUrl baseUrl;
    /** URI endpoint внутри выбранного базового URL. */
    private final String uri;

    /**
     * Полный URL endpoint (base URL + URI).
     */
    public String getDefaultUrl() {
        return baseUrl.getDefaultBaseUrl() + uri;
    }

    /**
     * Типы, которые реально ставятся в очередь {@code ozon_api_events}.
     */
    public boolean isQueuedEvent() {
        return this == PRODUCT_LIST_PAGE
                || this == PRICES_CABINET
                || this == STOCKS_CABINET
                || this == ANALYTICS_DATA_CABINET
                || this == CAMPAIGNS_CABINET
                || this == CAMPAIGN_STATS_CABINET;
    }
}
