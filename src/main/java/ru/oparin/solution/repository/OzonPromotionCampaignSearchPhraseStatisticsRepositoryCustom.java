package ru.oparin.solution.repository;

import org.springframework.data.domain.Sort;
import ru.oparin.solution.dto.analytics.NormQueryClusterSortField;

import java.time.LocalDate;
import java.util.List;

/**
 * Пагинированная выборка агрегированных поисковых запросов Ozon с сортировкой и поиском.
 */
public interface OzonPromotionCampaignSearchPhraseStatisticsRepositoryCustom {

    long countAggregatedClusters(
            Long campaignId,
            LocalDate dateFrom,
            LocalDate dateTo,
            Long sku,
            String searchPattern
    );

    List<OzonPromotionCampaignSearchPhraseStatisticsRepository.SearchPhraseClusterAggregateRow> findAggregatedClustersPage(
            Long campaignId,
            LocalDate dateFrom,
            LocalDate dateTo,
            Long sku,
            String searchPattern,
            NormQueryClusterSortField sortBy,
            Sort.Direction sortDir,
            int limit,
            int offset
    );

    OzonPromotionCampaignSearchPhraseStatisticsRepository.SearchPhraseClusterTotalsRow findTotalsByCampaignAndPeriod(
            Long campaignId,
            LocalDate dateFrom,
            LocalDate dateTo,
            Long sku,
            String searchPattern
    );
}
