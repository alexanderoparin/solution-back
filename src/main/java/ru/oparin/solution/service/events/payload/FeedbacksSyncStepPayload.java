package ru.oparin.solution.service.events.payload;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Builder;

import java.time.LocalDate;

/**
 * Параметры одного шага (одной страницы) пошаговой синхронизации отзывов.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@Builder
public record FeedbacksSyncStepPayload(
        Long runId,
        boolean isAnswered,
        int skip,
        LocalDate dateFrom,
        LocalDate dateTo,
        boolean includeStocks
) {
}
