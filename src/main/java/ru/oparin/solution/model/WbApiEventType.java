package ru.oparin.solution.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Типы событий WB API с привязкой к endpoint и лимитам по типу токена.
 * Используется как единый источник метаданных для планировщика и rate-limit.
 */
@Getter
@RequiredArgsConstructor
public enum WbApiEventType {
    CONTENT_CARDS_LIST_PAGE(
            WbApiBaseUrl.CONTENT,
            "/content/v2/get/cards/list",
            1L,
            1L),
    CONTENT_CARDS_TRASH(
            WbApiBaseUrl.CONTENT,
            "/content/v2/get/cards/trash",
            1L,
            1L),
    ANALYTICS_SALES_FUNNEL_NMID(
            WbApiBaseUrl.ANALYTICS,
            "/api/analytics/v3/sales-funnel/products/history",
            1_800L,
            20L),
    PRICES_CABINET_WITH_SPP(
            WbApiBaseUrl.DISCOUNTS_PRICES,
            "/api/v2/list/goods/filter",
            1L,
            1L),
    PROMOTION_COUNT(
            WbApiBaseUrl.PROMOTION,
            "/adv/v1/promotion/count",
            900L,
            1L),
    PROMOTION_ADVERTS_BATCH(
            WbApiBaseUrl.PROMOTION,
            "/api/advert/v2/adverts",
            3_600L,
            1L),
    PROMOTION_STATS_BATCH(
            WbApiBaseUrl.PROMOTION,
            "/adv/v3/fullstats",
            3_600L,
            20L),
    FEEDBACKS_SYNC_CABINET(
            WbApiBaseUrl.FEEDBACKS,
            "/api/v1/feedbacks",
            720L,
            1L),
    PROMOTION_CALENDAR_SYNC_CABINET(
            WbApiBaseUrl.DP_CALENDAR,
            "/api/v1/calendar/promotions",
            3_600L,
            1L),
    PROMOTION_CALENDAR_NOMENCLATURES(
            WbApiBaseUrl.DP_CALENDAR,
            "/api/v1/calendar/promotions/nomenclatures",
            3_600L,
            20L),
    WAREHOUSES_SYNC_CABINET(
            WbApiBaseUrl.SUPPLIES,
            "/api/v1/warehouses",
            43_200L,
            10L),
    STOCKS_BY_NMID(
            WbApiBaseUrl.ANALYTICS,
            "/api/v2/stocks-report/products/sizes",
            1_800L,
            20L),
    COMMON_SELLER_INFO(
            WbApiBaseUrl.COMMON,
            "/api/v1/seller-info",
            1L,
            1L),
    /** Заказы продавца за дату (statistics-api); паузы совпадают с {@code WbHttpSuccessSpacingMsResolver} для этого пути. */
    STATISTICS_SUPPLIER_ORDERS(
            WbApiBaseUrl.STATISTICS,
            "/api/v1/supplier/orders",
            60L,
            60L);

    /** Базовый URL группы WB API для данного события. */
    private final WbApiBaseUrl baseUrl;
    /** URI endpoint внутри выбранного базового URL. */
    private final String uri;
    /** Пауза между запросами для базового токена (сек). */
    private final long requestDelayBasicSeconds;
    /** Пауза между запросами для персонального токена (сек). */
    private final long requestDelayPersonalSeconds;

    /**
     * Возвращает полный URL endpoint по умолчанию (base URL + URI пути).
     *
     * @return полный URL endpoint
     */
    public String getDefaultUrl() {
        return baseUrl.getDefaultBaseUrl() + uri;
    }

    /**
     * Возвращает интервал между запросами к endpoint в миллисекундах для указанного типа токена.
     *
     * @param tokenType тип токена кабинета
     * @return задержка между запросами в миллисекундах
     */
    public long getRequestDelayMs(CabinetTokenType tokenType) {
        long seconds = tokenType == CabinetTokenType.PERSONAL ? requestDelayPersonalSeconds : requestDelayBasicSeconds;
        return seconds * 1_000L;
    }
}
