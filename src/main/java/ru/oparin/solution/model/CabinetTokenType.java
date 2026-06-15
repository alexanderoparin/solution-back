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

    /**
     * Отчёт item-rating доступен только для персонального/сервисного токена WB, не для базового.
     */
    public boolean supportsItemRating() {
        return this != BASIC;
    }

    /**
     * Эффективный тип токена кабинета (null трактуется как BASIC).
     */
    public static CabinetTokenType effective(CabinetTokenType tokenType) {
        return tokenType != null ? tokenType : BASIC;
    }
}
