package ru.oparin.solution.dto;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Поля сортировки обзора биллинга кабинетов (админка).
 */
@Getter
@RequiredArgsConstructor
public enum CabinetBillingSortField {
    CABINET_ID("CABINET_ID"),
    CABINET_NAME("CABINET_NAME"),
    SELLER_EMAIL("SELLER_EMAIL"),
    MAIN("MAIN"),
    CAMPAIGN("CAMPAIGN"),
    AB("AB");

    /** По умолчанию: сначала кабинеты с большим ID. */
    public static final String DEFAULT_REQUEST_VALUE = "CABINET_ID";

    private final String paramValue;
}
