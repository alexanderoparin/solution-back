package ru.oparin.solution.dto.analytics;

import java.time.LocalDateTime;

/**
 * Доступность запуска/паузы РК для кабинета (учёт read-only токена WB).
 */
public record PromotionControlCapabilitiesDto(
        boolean canControl,
        String message,
        long nextAvailableInSeconds,
        LocalDateTime blockedUntil
) {
    public static PromotionControlCapabilitiesDto allowed() {
        return new PromotionControlCapabilitiesDto(true, null, 0L, null);
    }
}
