package ru.oparin.solution.service.events.payload;

import lombok.Builder;

import java.time.LocalDate;

/**
 * Payload загрузки дневной статистики РК Ozon Performance по кабинету.
 */
@Builder
public record OzonCampaignStatsCabinetPayload(
        LocalDate dateFrom,
        LocalDate dateTo,
        String productStatsReportUuid,
        Integer productStatsBatchStart
) {
    public int resolveProductStatsBatchStart() {
        return productStatsBatchStart != null ? productStatsBatchStart : 0;
    }
}
