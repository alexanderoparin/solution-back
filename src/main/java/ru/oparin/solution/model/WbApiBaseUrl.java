package ru.oparin.solution.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.net.URI;

/**
 * Единый перечень базовых URL Wildberries API. Менять хосты только здесь (в т.ч. вместо configmap / application.yaml).
 */
@Getter
@AllArgsConstructor
public enum WbApiBaseUrl {
    /** Статистика, заказы (statistics-api) */
    STATISTICS("https://statistics-api.wildberries.ru"),
    /** Контент, карточки (content-api) */
    CONTENT("https://content-api.wildberries.ru"),
    /** Аналитика, воронка, остатки (seller-analytics-api) */
    ANALYTICS("https://seller-analytics-api.wildberries.ru"),
    /** Продвижение, реклама (advert-api) */
    PROMOTION("https://advert-api.wildberries.ru"),
    /** Цены и скидки, товары (discounts-prices-api) */
    DISCOUNTS_PRICES("https://discounts-prices-api.wildberries.ru"),
    /** Календарь акций (dp-calendar-api) */
    DP_CALENDAR("https://dp-calendar-api.wildberries.ru"),
    /** Маркетплейс, FBS/DBS (marketplace-api) */
    MARKETPLACE("https://marketplace-api.wildberries.ru"),
    /** Поставки, склады (supplies-api) */
    SUPPLIES("https://supplies-api.wildberries.ru"),
    /** Отзывы и вопросы (feedbacks-api) */
    FEEDBACKS("https://feedbacks-api.wildberries.ru"),
    /** Тарифы, новости, seller-info (common-api) */
    COMMON("https://common-api.wildberries.ru");

    private final String defaultBaseUrl;

    /**
     * Полный URL метода {@code /ping} для проверки доступа к домену.
     *
     * @return базовый URL + {@code /ping}
     */
    public String getPingUrl() {
        return defaultBaseUrl + "/ping";
    }

    /**
     * Имя хоста без схемы (для логов, DNS-ошибок).
     *
     * @return host из HTTPS-URL
     */
    public String getHost() {
        return URI.create(defaultBaseUrl).getHost();
    }
}
