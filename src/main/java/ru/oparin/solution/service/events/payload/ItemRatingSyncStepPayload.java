package ru.oparin.solution.service.events.payload;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Builder;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Параметры одного шага (одной страницы) синхронизации рейтинга через item-rating.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@Builder
public record ItemRatingSyncStepPayload(
        int offset,
        LocalDateTime syncStartedAt,
        LocalDate dateFrom,
        LocalDate dateTo,
        boolean includeStocks
) {
}
