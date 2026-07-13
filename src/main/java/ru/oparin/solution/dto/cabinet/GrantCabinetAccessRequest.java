package ru.oparin.solution.dto.cabinet;

import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import ru.oparin.solution.model.AccountType;
import ru.oparin.solution.model.CabinetAccessSection;

import java.time.LocalDateTime;
import java.util.List;

@Builder
public record GrantCabinetAccessRequest(
        String email,
        String comment,
        List<CabinetAccessSection> sections,
        LocalDateTime validUntil,
        @NotNull(message = "Выберите тип аккаунта")
        AccountType accountType
) {
}
