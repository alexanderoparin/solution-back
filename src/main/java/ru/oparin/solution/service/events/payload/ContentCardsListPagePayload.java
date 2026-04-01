package ru.oparin.solution.service.events.payload;

import lombok.Builder;

import java.time.LocalDate;

@Builder
public record ContentCardsListPagePayload(
        LocalDate dateFrom,
        LocalDate dateTo,
        boolean includeStocks,
        Long cursorNmId,
        String cursorUpdatedAt
) {
}
