package ru.oparin.solution.dto;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Поля сортировки плоского списка кабинетов (админка).
 */
@Getter
@RequiredArgsConstructor
public enum ManagedCabinetSortField {
    CABINET_ID("CABINET_ID"),
    CABINET_NAME("CABINET_NAME"),
    SELLER_EMAIL("SELLER_EMAIL"),
    LAST_DATA_UPDATE_AT("LAST_DATA_UPDATE_AT"),
    LAST_STOCKS_UPDATE_AT("LAST_STOCKS_UPDATE_AT");

    /** По умолчанию: сначала самые старые даты основного обновления. */
    public static final String DEFAULT_REQUEST_VALUE = "LAST_DATA_UPDATE_AT";

    private final String paramValue;
}
