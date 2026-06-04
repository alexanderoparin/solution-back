package ru.oparin.solution.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.oparin.solution.model.CampaignChangeLog;

public interface CampaignChangeLogRepository extends JpaRepository<CampaignChangeLog, Long> {

    Page<CampaignChangeLog> findByCampaignIdAndCabinetIdOrderByCreatedAtDesc(
            Long campaignId, Long cabinetId, Pageable pageable);
}
