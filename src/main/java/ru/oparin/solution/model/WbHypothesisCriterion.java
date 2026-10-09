package ru.oparin.solution.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Критерии оценки гипотезы.
 */
@Getter
@RequiredArgsConstructor
public enum WbHypothesisCriterion {
    DRR("drr", "ДРР", true),
    ORDERS("orders", "Заказы", false),
    CTR("ctr", "CTR", false),
    CPC("cpc", "CPC", true),
    CPO("cpo", "CPO", true),
    VIEWS("views", "Показы", false),
    BUYOUT_PERCENT("buyout_percent", "Процент выкупа", false),
    AVG_POS("avg_pos", "Средние позиции", true);

    private final String key;
    private final String labelRu;
    /** {@code true}, если улучшение = уменьшение значения (затраты, позиция в выдаче). */
    private final boolean lowerIsBetter;

    /**
     * Разбирает ключ критерия из API/JSON.
     */
    public static WbHypothesisCriterion fromKey(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        for (WbHypothesisCriterion criterion : values()) {
            if (criterion.key.equalsIgnoreCase(key.trim())) {
                return criterion;
            }
        }
        return null;
    }
}
