package ru.oparin.solution.service.events.payload;

import lombok.Builder;

import java.time.LocalDateTime;

/**
 * Payload пошаговой синхронизации контент-рейтинга Ozon по SKU.
 */
@Builder
public record OzonContentRatingCabinetPayload(
        int offset,
        LocalDateTime syncStartedAt
) {
}
