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
public interface PromotionNormQueryStatisticsRepository
        extends JpaRepository<PromotionNormQueryStatistics, Long>, PromotionNormQueryStatisticsRepositoryCustom {

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
