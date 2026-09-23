package ru.oparin.solution.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ru.oparin.solution.model.OzonPromotionCampaign;

import java.util.List;

/**
 * Репозиторий рекламных кампаний Ozon.
 */
@Repository
public interface OzonPromotionCampaignRepository extends JpaRepository<OzonPromotionCampaign, Long> {

    List<OzonPromotionCampaign> findByCabinet_Id(Long cabinetId);

    java.util.Optional<OzonPromotionCampaign> findByCampaignIdAndCabinet_Id(Long campaignId, Long cabinetId);

    /**
     * Кампания с кабинетом и владельцем (resolve по прямой ссылке).
     */
    @Query("""
            SELECT c FROM OzonPromotionCampaign c
            JOIN FETCH c.cabinet cab
            JOIN FETCH cab.user
            WHERE c.campaignId = :campaignId
            """)
    java.util.Optional<OzonPromotionCampaign> findByCampaignIdWithCabinetOwner(@Param("campaignId") Long campaignId);

    /**
     * Выборка только campaign_id по кабинету пачкой (для пакетного удаления).
     */
    @Query("SELECT c.campaignId FROM OzonPromotionCampaign c WHERE c.cabinet.id = :cabinetId")
    List<Long> findCampaignIdByCabinet_Id(@Param("cabinetId") Long cabinetId, Pageable pageable);
}
