package ru.oparin.solution.service.events.payload;

import lombok.Builder;

@Builder
public record StocksByNmIdPayload(
        Long nmId
) {
}
