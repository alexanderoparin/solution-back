package ru.oparin.solution.service.events.payload;

import lombok.Builder;

import java.time.LocalDate;

@Builder
public record AnalyticsSalesFunnelPayload(
        Long nmId,
        LocalDate dateFrom,
        LocalDate dateTo,
        boolean includeStocks
) {
}
