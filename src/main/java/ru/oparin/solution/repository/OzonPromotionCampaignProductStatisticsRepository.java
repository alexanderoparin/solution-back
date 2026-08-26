package ru.oparin.solution.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ru.oparin.solution.model.OzonPromotionCampaignProductStatistics;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Репозиторий дневной статистики SKU в РК Ozon.
 */
@Repository
public interface OzonPromotionCampaignProductStatisticsRepository
        extends JpaRepository<OzonPromotionCampaignProductStatistics, Long> {

    List<OzonPromotionCampaignProductStatistics> findByCampaign_CampaignIdInAndSkuAndDateBetween(
            Collection<Long> campaignIds,
            Long sku,
            LocalDate dateFrom,
            LocalDate dateTo
    );

    /**
     * Product-stats по набору кампаний за период (для агрегации корзины/заказов в списке РК).
     */
    List<OzonPromotionCampaignProductStatistics> findByCampaign_CampaignIdInAndDateBetween(
            Collection<Long> campaignIds,
            LocalDate dateFrom,
            LocalDate dateTo
    );

    /**
     * Product-stats кабинета за период (для сводной аналитики).
     */
    @Query("""
            SELECT s FROM OzonPromotionCampaignProductStatistics s
            WHERE s.campaign.cabinet.id = :cabinetId
              AND s.date BETWEEN :dateFrom AND :dateTo
            """)
    List<OzonPromotionCampaignProductStatistics> findByCampaign_Cabinet_IdAndDateBetween(
            @Param("cabinetId") Long cabinetId,
            @Param("dateFrom") LocalDate dateFrom,
            @Param("dateTo") LocalDate dateTo
    );

    Optional<OzonPromotionCampaignProductStatistics> findByCampaign_CampaignIdAndSkuAndDate(
            Long campaignId,
            Long sku,
            LocalDate date
    );

    /**
     * Выборка только ID статистики по кабинету пачкой (для пакетного удаления).
     */
    @Query("SELECT s.id FROM OzonPromotionCampaignProductStatistics s WHERE s.campaign.cabinet.id = :cabinetId")
    List<Long> findIdByCampaign_Cabinet_Id(@Param("cabinetId") Long cabinetId, Pageable pageable);
}
