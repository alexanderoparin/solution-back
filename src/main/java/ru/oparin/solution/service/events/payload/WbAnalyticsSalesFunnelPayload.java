package ru.oparin.solution.service.events.payload;

import lombok.Builder;

import java.time.LocalDate;

@Builder
public record WbAnalyticsSalesFunnelPayload(
        Long nmId,
        LocalDate dateFrom,
        LocalDate dateTo,
        boolean includeStocks
) {
}
