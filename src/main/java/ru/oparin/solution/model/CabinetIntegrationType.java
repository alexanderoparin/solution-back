package ru.oparin.solution.model;

/**
 * Тип интеграции кабинета в {@code cabinet_integrations}.
 */
public enum CabinetIntegrationType {
    /** Wildberries API-токен. */
    WB_API,
    /** Ozon Seller API (Client-Id + Api-Key). */
    OZON_SELLER,
    /** Ozon Performance API (реклама). */
    OZON_PERFORMANCE
}
