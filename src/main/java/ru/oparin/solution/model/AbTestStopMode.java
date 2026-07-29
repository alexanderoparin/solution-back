package ru.oparin.solution.model;

/**
 * Критерий автоостановки А/Б-теста.
 */
public enum AbTestStopMode {
    /** Система сама решает, когда данных достаточно. */
    TRUST_US,
    /** Остановка по истечении выбранной длительности. */
    BY_DURATION
}
