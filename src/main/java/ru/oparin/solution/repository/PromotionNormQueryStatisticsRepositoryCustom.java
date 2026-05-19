package ru.oparin.solution.repository;

import org.springframework.data.domain.Sort;
import ru.oparin.solution.dto.analytics.NormQueryClusterSortField;

import java.time.LocalDate;
import java.util.List;

/**
 * Пагинированная выборка агрегированных кластеров с сортировкой и поиском.
 */
public interface PromotionNormQueryStatisticsRepositoryCustom {

    long countAggregatedClusters(
            Long campaignId,
            LocalDate dateFrom,
            LocalDate dateTo,
            Long nmId,
            String searchPattern
    );

    List<PromotionNormQueryStatisticsRepository.NormQueryClusterAggregateRow> findAggregatedClustersPage(
            Long campaignId,
            LocalDate dateFrom,
            LocalDate dateTo,
            Long nmId,
            String searchPattern,
            NormQueryClusterSortField sortBy,
            Sort.Direction sortDir,
            int limit,
            int offset
    );

    PromotionNormQueryStatisticsRepository.NormQueryClusterTotalsRow findTotalsByCampaignAndPeriod(
            Long campaignId,
            LocalDate dateFrom,
            LocalDate dateTo,
            Long nmId,
            String searchPattern
    );
}
