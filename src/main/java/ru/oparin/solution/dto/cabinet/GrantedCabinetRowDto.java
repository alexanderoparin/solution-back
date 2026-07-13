package ru.oparin.solution.dto.cabinet;

import lombok.Builder;
import ru.oparin.solution.model.CabinetAccessSection;

import java.time.LocalDateTime;
import java.util.List;

@Builder
public record GrantedCabinetRowDto(
        Long id,
        String name,
        LocalDateTime accessFrom,
        LocalDateTime accessUntil,
        LocalDateTime lastValidatedAt,
        Boolean apiKeyValid,
        LocalDateTime lastDataUpdateAt,
        String apiKeyMasked,
        String grantedByName,
        List<CabinetAccessSection> sections
) {
}
