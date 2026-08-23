package ru.oparin.solution.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
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

    List<Long> findCampaignIdByCabinet_Id(@Param("cabinetId") Long cabinetId, Pageable pageable);
}
