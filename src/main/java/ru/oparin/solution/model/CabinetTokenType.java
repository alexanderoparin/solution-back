package ru.oparin.solution.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Тип токена WB API кабинета.
 */
@AllArgsConstructor
@Getter
public enum CabinetTokenType {
    PERSONAL("Персональный"),
    BASIC("Базовый");

    private final String displayName;
}
