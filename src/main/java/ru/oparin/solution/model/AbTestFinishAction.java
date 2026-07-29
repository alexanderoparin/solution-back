package ru.oparin.solution.model;

/**
 * Действие с главным фото при завершении А/Б-теста.
 */
public enum AbTestFinishAction {
    /** Оставить вариант с лучшим CTR. */
    KEEP_WINNER,
    /** Вернуть исходное главное фото. */
    RESTORE_ORIGINAL
}
