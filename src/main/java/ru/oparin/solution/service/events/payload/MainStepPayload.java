package ru.oparin.solution.service.events.payload;

import lombok.Builder;

import java.time.LocalDate;

@Builder
public record MainStepPayload(
        LocalDate dateFrom,
        LocalDate dateTo,
        boolean includeStocks
) {
}
