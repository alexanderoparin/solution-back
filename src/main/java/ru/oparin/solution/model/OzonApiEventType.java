package ru.oparin.solution.model;

/**
 * Типы событий Ozon Seller API.
 */
public enum OzonApiEventType {
    /** Постраничная загрузка каталога товаров. */
    PRODUCT_LIST_PAGE,
    /** Загрузка цен по кабинету (все страницы /v5/product/info/prices). */
    PRICES_CABINET
}
