package ru.oparin.solution.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ru.oparin.solution.model.OzonPromotionCampaignSearchPhraseStatistics;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Репозиторий поисковых запросов (кластеров) в РК Ozon Performance.
 */
@Repository
public interface OzonPromotionCampaignSearchPhraseStatisticsRepository
        extends JpaRepository<OzonPromotionCampaignSearchPhraseStatistics, Long>,
        OzonPromotionCampaignSearchPhraseStatisticsRepositoryCustom {

    @Modifying
    @Query("DELETE FROM OzonPromotionCampaignSearchPhraseStatistics s "
            + "WHERE s.campaign.campaignId = :campaignId AND s.date BETWEEN :dateFrom AND :dateTo")
    void deleteByCampaignIdAndDateBetween(
            @Param("campaignId") Long campaignId,
            @Param("dateFrom") LocalDate dateFrom,
            @Param("dateTo") LocalDate dateTo
    );

    @Query("SELECT MAX(s.updatedAt) FROM OzonPromotionCampaignSearchPhraseStatistics s "
            + "WHERE s.campaign.campaignId = :campaignId AND s.date BETWEEN :dateFrom AND :dateTo "
            + "AND (:sku IS NULL OR s.sku = :sku OR s.sku IS NULL)")
    LocalDateTime findMaxUpdatedAt(
            @Param("campaignId") Long campaignId,
            @Param("dateFrom") LocalDate dateFrom,
            @Param("dateTo") LocalDate dateTo,
            @Param("sku") Long sku
    );

    interface SearchPhraseClusterAggregateRow {
        String getSearchPhrase();

        BigDecimal getAvgPos();

        Integer getClicks();

        Integer getAtbs();

        Integer getOrders();

        BigDecimal getSpend();

        BigDecimal getCpc();

        BigDecimal getCpo();
    }

    interface SearchPhraseClusterTotalsRow {
        BigDecimal getAvgPos();

        Integer getClicks();

        Integer getAtbs();

        Integer getOrders();

        BigDecimal getSpend();

        BigDecimal getCpc();

        BigDecimal getCpo();
    }
}
