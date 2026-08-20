package ru.oparin.solution.model;

/**
 * Действие с главным фото при завершении А/Б-теста.
 */
public enum WbAbTestFinishAction {
    /** Оставить вариант с лучшим CTR. */
    KEEP_WINNER,
    /** Вернуть исходное главное фото. */
    RESTORE_ORIGINAL
}
