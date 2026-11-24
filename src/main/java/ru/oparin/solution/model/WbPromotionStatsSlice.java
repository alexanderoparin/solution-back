package ru.oparin.solution.model;

/**
 * Значения разрезов статистики РК WB (v4 fullstats) и маркер исторического агрегата.
 */
public final class WbPromotionStatsSlice {

    /**
     * Исторический агрегат без разбивки (данные, перенесённые из v3).
     */
    public static final String ALL = "all";

    /**
     * Поиск.
     */
    public static final String PLACEMENT_SEARCH = "search";

    /**
     * Рекомендации.
     */
    public static final String PLACEMENT_RECOMMENDATION = "recommendation";

    /**
     * Прямая конверсия (рекламируемый товар).
     */
    public static final String CONVERSION_DIRECT = "direct";

    /**
     * Заказ другого варианта из карточки.
     */
    public static final String CONVERSION_MULTICARD = "multicard";

    /**
     * Заказ другого товара.
     */
    public static final String CONVERSION_ASSOCIATED = "associated";

    private WbPromotionStatsSlice() {
    }
}
