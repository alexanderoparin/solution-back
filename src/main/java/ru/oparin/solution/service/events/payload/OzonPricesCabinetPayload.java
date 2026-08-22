package ru.oparin.solution.service.events.payload;

import lombok.Builder;

/**
 * Payload загрузки цен Ozon по кабинету.
 */
@Builder
public record OzonPricesCabinetPayload(
        boolean includeStocks
) {
}
