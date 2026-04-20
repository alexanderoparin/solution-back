package ru.oparin.solution.model;

public enum WbApiEventStatus {
    CREATED,
    RUNNING,
    SUCCESS,
    FAILED_RETRYABLE,
    FAILED_FINAL,
    FAILED_WITH_FALLBACK,
    DEFERRED_RATE_LIMIT,
    CANCELLED
}
