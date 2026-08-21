package ru.oparin.solution.model;

/**
 * Маркетплейс кабинета. Задаётся при создании и не меняется.
 * Один кабинет = один маркетплейс (WB и Ozon не смешиваются).
 */
public enum MarketplaceType {
    /** Wildberries */
    WB,
    /** Ozon */
    OZON
}
