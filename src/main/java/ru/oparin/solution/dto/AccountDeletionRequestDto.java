package ru.oparin.solution.dto;

import lombok.Builder;
import ru.oparin.solution.model.AccountDeletionReason;

@Builder
public record AccountDeletionRequestDto(
        AccountDeletionReason reason,
        String comment
) {
}
