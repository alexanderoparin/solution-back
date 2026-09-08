package ru.oparin.solution.dto;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Поля сортировки для списка пользователей в админке.
 */
@Getter
@RequiredArgsConstructor
public enum UserSortField {
    ID("id"),
    EMAIL("email"),
    ROLE("role"),
    IS_ACTIVE("isActive"),
    OWNER_EMAIL("ownerEmail"),
    CREATED_AT("createdAt"),
    LAST_SEEN_AT("lastSeenAt"),
    LAST_DATA_UPDATE_AT("lastDataUpdateAt"),
    LAST_DATA_UPDATE_REQUESTED_AT("lastDataUpdateRequestedAt");

    public static final String DEFAULT_REQUEST_VALUE = "ID";

    private final String paramValue;
}
