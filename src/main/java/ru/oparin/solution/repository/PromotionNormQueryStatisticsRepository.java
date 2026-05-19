package ru.oparin.solution.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ru.oparin.solution.model.PromotionNormQueryStatistics;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Репозиторий статистики по поисковым кластерам рекламных кампаний.
 */
@Repository
public interface PromotionNormQueryStatisticsRepository extends JpaRepository<PromotionNormQueryStatistics, Long> {

    @Modifying
    @Query("DELETE FROM PromotionNormQueryStatistics s WHERE s.campaign.advertId IN :campaignIds "
            + "AND s.date BETWEEN :dateFrom AND :dateTo")
    void deleteByCampaignAdvertIdInAndDateBetween(
            @Param("campaignIds") List<Long> campaignIds,
            @Param("dateFrom") LocalDate dateFrom,
            @Param("dateTo") LocalDate dateTo
    );

    void deleteByCampaign_AdvertId(Long advertId);

    void deleteByCampaign_AdvertIdIn(List<Long> advertIds);

    @Query("SELECT s.id FROM PromotionNormQueryStatistics s WHERE s.campaign.cabinet.id = :cabinetId")
    List<Long> findIdByCampaign_Cabinet_Id(@Param("cabinetId") Long cabinetId, Pageable pageable);

    @Query(value = """
            SELECT
                s.norm_query AS normQuery,
                CASE WHEN COALESCE(SUM(s.clicks), 0) > 0
                    THEN SUM(s.avg_pos * s.clicks) / SUM(s.clicks)
                    ELSE AVG(s.avg_pos) END AS avgPos,
                COALESCE(SUM(s.clicks), 0) AS clicks,
                COALESCE(SUM(s.atbs), 0) AS atbs,
                COALESCE(SUM(s.orders), 0) AS orders,
                COALESCE(SUM(s.spend), 0) AS spend,
                CASE WHEN COALESCE(SUM(s.clicks), 0) > 0
                    THEN SUM(s.spend) / SUM(s.clicks)
                    ELSE NULL END AS cpc
            FROM solution.promotion_norm_query_statistics s
            WHERE s.campaign_id = :campaignId
              AND s.date BETWEEN :dateFrom AND :dateTo
              AND (:nmId IS NULL OR s.nm_id = :nmId)
            GROUP BY s.norm_query
            ORDER BY COALESCE(SUM(s.clicks), 0) DESC
            """, nativeQuery = true)
    List<NormQueryClusterAggregateRow> findAggregatedByCampaignAndPeriod(
            @Param("campaignId") Long campaignId,
            @Param("dateFrom") LocalDate dateFrom,
            @Param("dateTo") LocalDate dateTo,
            @Param("nmId") Long nmId
    );

    @Query(value = """
            SELECT
                CASE WHEN COALESCE(SUM(s.clicks), 0) > 0
                    THEN SUM(s.avg_pos * s.clicks) / SUM(s.clicks)
                    ELSE AVG(s.avg_pos) END AS avgPos,
                COALESCE(SUM(s.clicks), 0) AS clicks,
                COALESCE(SUM(s.atbs), 0) AS atbs,
                COALESCE(SUM(s.orders), 0) AS orders,
                COALESCE(SUM(s.spend), 0) AS spend,
                CASE WHEN COALESCE(SUM(s.clicks), 0) > 0
                    THEN SUM(s.spend) / SUM(s.clicks)
                    ELSE NULL END AS cpc
            FROM solution.promotion_norm_query_statistics s
            WHERE s.campaign_id = :campaignId
              AND s.date BETWEEN :dateFrom AND :dateTo
              AND (:nmId IS NULL OR s.nm_id = :nmId)
            """, nativeQuery = true)
    NormQueryClusterTotalsRow findTotalsByCampaignAndPeriod(
            @Param("campaignId") Long campaignId,
            @Param("dateFrom") LocalDate dateFrom,
            @Param("dateTo") LocalDate dateTo,
            @Param("nmId") Long nmId
    );

    @Query("SELECT MAX(s.updatedAt) FROM PromotionNormQueryStatistics s "
            + "WHERE s.campaign.advertId = :campaignId AND s.date BETWEEN :dateFrom AND :dateTo "
            + "AND (:nmId IS NULL OR s.nmId = :nmId)")
    LocalDateTime findMaxUpdatedAt(
            @Param("campaignId") Long campaignId,
            @Param("dateFrom") LocalDate dateFrom,
            @Param("dateTo") LocalDate dateTo,
            @Param("nmId") Long nmId
    );

    interface NormQueryClusterAggregateRow {
        String getNormQuery();

        java.math.BigDecimal getAvgPos();

        Integer getClicks();

        Integer getAtbs();

        Integer getOrders();

        java.math.BigDecimal getSpend();

        java.math.BigDecimal getCpc();
    }

    interface NormQueryClusterTotalsRow {
        java.math.BigDecimal getAvgPos();

        Integer getClicks();

        Integer getAtbs();

        Integer getOrders();

        java.math.BigDecimal getSpend();

        java.math.BigDecimal getCpc();
    }
}
