package ru.oparin.solution.model;

/**
 * Код статусной строки А/Б-теста в списке.
 */
public enum WbAbTestInsightCode {
    /** Накоплено мало данных. */
    DATA_LOW,
    /** Существенной разницы между вариантами нет. */
    NO_DIFF,
    /** Есть явный лидер по CTR. */
    HAS_LEADER
}
