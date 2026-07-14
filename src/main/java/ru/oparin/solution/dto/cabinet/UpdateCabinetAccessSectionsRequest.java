package ru.oparin.solution.dto.cabinet;

import jakarta.validation.constraints.NotEmpty;
import lombok.Builder;
import ru.oparin.solution.model.CabinetAccessSection;

import java.util.List;

/**
 * Обновление списка разделов доступа или приглашения.
 */
@Builder
public record UpdateCabinetAccessSectionsRequest(
        @NotEmpty(message = "Выберите хотя бы один раздел")
        List<CabinetAccessSection> sections
) {
}
