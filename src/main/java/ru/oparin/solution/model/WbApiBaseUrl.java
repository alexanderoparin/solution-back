package ru.oparin.solution.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Известные базовые URL WB API.
 */
@Getter
@AllArgsConstructor
public enum WbApiBaseUrl {
    STATISTICS("https://statistics-api.wildberries.ru"),
    CONTENT("https://content-api.wildberries.ru"),
    ANALYTICS("https://seller-analytics-api.wildberries.ru"),
    PROMOTION("https://advert-api.wildberries.ru"),
    DISCOUNTS_PRICES("https://discounts-prices-api.wildberries.ru"),
    DP_CALENDAR("https://dp-calendar-api.wildberries.ru"),
    MARKETPLACE("https://marketplace-api.wildberries.ru"),
    SUPPLIES("https://supplies-api.wildberries.ru"),
    FEEDBACKS("https://feedbacks-api.wildberries.ru");

    private final String defaultBaseUrl;
}
