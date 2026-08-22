package ru.oparin.solution.model;

/**
 * Статус события Ozon API.
 */
public enum OzonApiEventStatus {
    CREATED,
    RUNNING,
    SUCCESS,
    FAILED_RETRYABLE,
    FAILED_FINAL,
    DEFERRED_RATE_LIMIT,
    CANCELLED
}
