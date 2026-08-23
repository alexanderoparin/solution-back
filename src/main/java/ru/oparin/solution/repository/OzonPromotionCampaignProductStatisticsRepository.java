package ru.oparin.solution.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
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

    Optional<OzonPromotionCampaignProductStatistics> findByCampaign_CampaignIdAndSkuAndDate(
            Long campaignId,
            Long sku,
            LocalDate date
    );

    List<Long> findIdByCampaign_Cabinet_Id(@Param("cabinetId") Long cabinetId, Pageable pageable);
}
