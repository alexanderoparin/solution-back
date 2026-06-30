package ru.oparin.solution.dto.analytics;

import lombok.Getter;

/**
 * Поле сортировки списка поисковых кластеров (алиас в агрегирующем подзапросе).
 */
@Getter
public enum NormQueryClusterSortField {

    NORM_QUERY("normQuery"),
    AVG_POS("avgPos"),
    CLICKS("clicks"),
    ATBS("atbs"),
    ORDERS("orders"),
    SPEND("spend"),
    CPO("cpo"),
    CPC("cpc");

    private final String sqlAlias;

    NormQueryClusterSortField(String sqlAlias) {
        this.sqlAlias = sqlAlias;
    }

    /**
     * Разбор параметра REST ({@code normQuery}, {@code clicks}, …).
     */
    public static NormQueryClusterSortField fromParam(String value) {
        if (value == null || value.isBlank()) {
            return CLICKS;
        }
        String normalized = value.trim();
        for (NormQueryClusterSortField field : values()) {
            if (field.name().equalsIgnoreCase(normalized.replace('-', '_'))
                    || field.sqlAlias.equalsIgnoreCase(normalized)) {
                return field;
            }
        }
        try {
            return valueOf(normalized.toUpperCase());
        } catch (IllegalArgumentException e) {
            return CLICKS;
        }
    }
}
