package ru.oparin.solution.model;

/**
 * Разделы сервиса, к которым может быть выдан доступ к кабинету.
 */
public enum CabinetAccessSection {
    /** Товары */
    PRODUCTS,
    /** Сводная аналитика */
    SUMMARY,
    /** Рекламные кампании */
    AD_CAMPAIGNS,
    /** Управление РК */
    CAMPAIGN_MANAGE
}
