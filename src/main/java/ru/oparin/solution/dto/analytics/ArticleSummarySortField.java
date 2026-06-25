package ru.oparin.solution.dto.analytics;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Поле сортировки списка артикулов в аналитике.
 */
@Getter
@RequiredArgsConstructor
public enum ArticleSummarySortField {

    WB_CREATED_AT("wbCreatedAt");

    public static final String DEFAULT_REQUEST_VALUE = "WB_CREATED_AT";

    private final String paramValue;

    public static ArticleSummarySortField fromParam(String value) {
        if (value == null || value.isBlank()) {
            return WB_CREATED_AT;
        }
        for (ArticleSummarySortField field : values()) {
            if (field.name().equalsIgnoreCase(value) || field.paramValue.equalsIgnoreCase(value)) {
                return field;
            }
        }
        return WB_CREATED_AT;
    }
}
