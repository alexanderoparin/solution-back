package ru.oparin.solution.service.events.payload;

import lombok.Builder;

/**
 * Payload асинхронного старта А/Б-теста.
 */
@Builder
public record AbTestStartPayload(
        Long abTestId
) {
}
