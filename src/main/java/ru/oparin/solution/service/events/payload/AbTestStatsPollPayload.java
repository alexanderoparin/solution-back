package ru.oparin.solution.service.events.payload;

import lombok.Builder;

/**
 * Payload асинхронного опроса статистики А/Б-теста.
 */
@Builder
public record AbTestStatsPollPayload(
        Long abTestId
) {
}
