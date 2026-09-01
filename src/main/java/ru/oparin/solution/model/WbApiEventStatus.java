package ru.oparin.solution.model;

/**
 * Статус обработки события очереди Wildberries API.
 */
public enum WbApiEventStatus {
    CREATED,
    RUNNING,
    SUCCESS,
    FAILED_RETRYABLE,
    FAILED_FINAL,
    FAILED_WITH_FALLBACK,
    DEFERRED_RATE_LIMIT,
    SKIPPED_NO_BUDGET,
    CANCELLED
}
