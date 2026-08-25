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
        Integer productStatsBatchStart,
        Boolean productStatsDone,
        String searchPhrasesReportUuid,
        Integer searchPhrasesBatchStart
) {
    public int resolveProductStatsBatchStart() {
        return productStatsBatchStart != null ? productStatsBatchStart : 0;
    }

    public int resolveSearchPhrasesBatchStart() {
        return searchPhrasesBatchStart != null ? searchPhrasesBatchStart : 0;
    }

    public boolean isProductStatsDone() {
        return Boolean.TRUE.equals(productStatsDone);
    }
}
