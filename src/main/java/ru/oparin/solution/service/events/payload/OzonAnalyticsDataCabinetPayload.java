package ru.oparin.solution.service.events.payload;

import lombok.Builder;

import java.time.LocalDate;

/**
 * Payload загрузки аналитики продаж Ozon по кабинету.
 */
@Builder
public record OzonAnalyticsDataCabinetPayload(
        LocalDate dateFrom,
        LocalDate dateTo
) {
}
