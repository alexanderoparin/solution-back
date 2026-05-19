package ru.oparin.solution.service.events.payload;

import lombok.Builder;

import java.time.LocalDate;
import java.util.List;

/**
 * Полезная нагрузка события {@code PROMOTION_NORMQUERY_STATS_BATCH}:
 * загрузка статистики поисковых кластеров для батча кампаний за период.
 *
 * @param campaignIds   список advertId кампаний в батче
 * @param batchIndex    порядковый номер батча в рамках периода (для dedupKey)
 * @param dateFrom      начало периода синхронизации
 * @param dateTo        конец периода синхронизации
 * @param includeStocks признак из главного шага очереди (на логику normquery не влияет)
 */
@Builder
public record PromotionNormQueryStatsBatchPayload(
        List<Long> campaignIds,
        int batchIndex,
        LocalDate dateFrom,
        LocalDate dateTo,
        boolean includeStocks
) {
}
