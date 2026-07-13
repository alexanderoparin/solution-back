package ru.oparin.solution.dto.cabinet;

import lombok.Builder;

import java.time.LocalDateTime;

@Builder
public record OwnedCabinetRowDto(
        Long id,
        String name,
        LocalDateTime createdAt,
        LocalDateTime lastValidatedAt,
        Boolean apiKeyValid,
        LocalDateTime lastDataUpdateAt,
        String apiKeyMasked
) {
}
