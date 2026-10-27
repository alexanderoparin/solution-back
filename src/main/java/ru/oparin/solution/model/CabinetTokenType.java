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
     * Рейтинг на витрине (reviewRating) синкаем из публичного catalog API — доступен для любого токена.
     */
    public boolean supportsItemRating() {
        return true;
    }

    /**
     * Частый опрос fullstats (чаще 1 раза в час) доступен не для базового токена.
     * У базового лимит {@code GET /adv/v3/fullstats} — 1 запрос в час.
     */
    public boolean supportsFrequentFullstats() {
        return this != BASIC;
    }

    /**
     * Эффективный тип токена кабинета (null трактуется как BASIC).
     */
    public static CabinetTokenType effective(CabinetTokenType tokenType) {
        return tokenType != null ? tokenType : BASIC;
    }
}
