package ru.oparin.solution.dto;

import lombok.Builder;
import ru.oparin.solution.model.AccountDeletionReason;
import ru.oparin.solution.model.AccountDeletionRequestStatus;

import java.time.LocalDateTime;

@Builder
public record AccountDeletionRequestAdminDto(
        Long id,
        Long userId,
        String userEmail,
        String userName,
        AccountDeletionReason reason,
        String comment,
        AccountDeletionRequestStatus status,
        LocalDateTime createdAt,
        LocalDateTime processedAt,
        String processedByEmail
) {
}
