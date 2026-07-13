package ru.oparin.solution.dto;

import lombok.Builder;
import ru.oparin.solution.model.AccountDeletionRequestStatus;

@Builder
public record AccountDeletionStatusDto(
        boolean hasPendingRequest,
        AccountDeletionRequestStatus status,
        String message
) {
}
