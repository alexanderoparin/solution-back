package ru.oparin.solution.service.events.payload;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Builder;

import java.time.LocalDate;

@JsonIgnoreProperties(ignoreUnknown = true)
@Builder
public record MainStepPayload(
        LocalDate dateFrom,
        LocalDate dateTo,
        boolean includeStocks
) {
}
