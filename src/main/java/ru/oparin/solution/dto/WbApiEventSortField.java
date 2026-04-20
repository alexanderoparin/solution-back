package ru.oparin.solution.dto;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum WbApiEventSortField {
    ID("id"),
    EVENT_TYPE("eventType"),
    STATUS("status"),
    CABINET_ID("cabinet.id"),
    ATTEMPT_COUNT("attemptCount"),
    MAX_ATTEMPTS("maxAttempts"),
    STARTED_AT("startedAt"),
    NEXT_ATTEMPT_AT("nextAttemptAt"),
    CREATED_AT("createdAt"),
    FINISHED_AT("finishedAt");

    public static final String DEFAULT_REQUEST_VALUE = "ID";

    private final String fieldPath;
}
