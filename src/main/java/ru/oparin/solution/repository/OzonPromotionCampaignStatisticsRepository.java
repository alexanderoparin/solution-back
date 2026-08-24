package ru.oparin.solution.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ru.oparin.solution.model.OzonPromotionCampaignStatistics;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Репозиторий ежедневной статистики РК Ozon.
 */
@Repository
public interface OzonPromotionCampaignStatisticsRepository extends JpaRepository<OzonPromotionCampaignStatistics, Long> {

    List<OzonPromotionCampaignStatistics> findByCampaign_CampaignIdInAndDateBetween(
            Collection<Long> campaignIds,
            LocalDate dateFrom,
            LocalDate dateTo
    );

    Optional<OzonPromotionCampaignStatistics> findByCampaign_CampaignIdAndDate(Long campaignId, LocalDate date);

    /**
     * Выборка только ID статистики по кабинету пачкой (для пакетного удаления).
     */
    @Query("SELECT s.id FROM OzonPromotionCampaignStatistics s WHERE s.campaign.cabinet.id = :cabinetId")
    List<Long> findIdByCampaign_Cabinet_Id(@Param("cabinetId") Long cabinetId, Pageable pageable);
}
