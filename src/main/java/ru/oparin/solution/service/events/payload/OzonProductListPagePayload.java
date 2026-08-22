package ru.oparin.solution.service.events.payload;

import lombok.Builder;

/**
 * Payload постраничной загрузки каталога Ozon.
 */
@Builder
public record OzonProductListPagePayload(
        String lastId,
        boolean includeStocks
) {
}
