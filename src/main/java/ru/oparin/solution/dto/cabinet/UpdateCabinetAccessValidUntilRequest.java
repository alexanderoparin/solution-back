package ru.oparin.solution.dto.cabinet;

import lombok.Builder;

import java.time.LocalDateTime;

/**
 * Обновление срока окончания доступа или приглашения.
 * {@code validUntil = null} — бессрочный доступ.
 */
@Builder
public record UpdateCabinetAccessValidUntilRequest(
        LocalDateTime validUntil
) {
}
