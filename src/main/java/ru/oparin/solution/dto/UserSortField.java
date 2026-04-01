package ru.oparin.solution.dto;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum UserSortField {
    EMAIL("email"),
    ROLE("role"),
    IS_ACTIVE("isActive"),
    CREATED_AT("createdAt"),
    LAST_DATA_UPDATE_AT("lastDataUpdateAt"),
    LAST_DATA_UPDATE_REQUESTED_AT("lastDataUpdateRequestedAt");

    public static final String DEFAULT_REQUEST_VALUE = "LAST_DATA_UPDATE_AT";

    private final String paramValue;
}
