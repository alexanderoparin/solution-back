package ru.oparin.solution.dto.cabinet;

import lombok.Builder;
import ru.oparin.solution.model.MarketplaceType;

import java.time.LocalDateTime;

@Builder
public record OwnedCabinetRowDto(
        Long id,
        String name,
        MarketplaceType marketplaceType,
        LocalDateTime createdAt,
        LocalDateTime lastValidatedAt,
        Boolean apiKeyValid,
        LocalDateTime lastDataUpdateAt,
        String apiKeyMasked
) {
}
