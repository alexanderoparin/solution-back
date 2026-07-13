package ru.oparin.solution.dto;

import lombok.Builder;

import java.time.LocalDateTime;

/**
 * Краткая информация о подписке для профиля.
 */
@Builder
public record ProfileSubscriptionSummaryDto(
        String planName,
        String planCode,
        String statusLabel,
        boolean active,
        LocalDateTime expiresAt,
        LocalDateTime nextBillingAt,
        boolean autoRenew,
        String freePlanHint
) {
}
