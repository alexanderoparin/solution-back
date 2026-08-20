package ru.oparin.solution.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.oparin.solution.model.WbCampaignChangeLog;

public interface WbCampaignChangeLogRepository extends JpaRepository<WbCampaignChangeLog, Long> {

    Page<WbCampaignChangeLog> findByCampaignIdAndCabinetIdOrderByCreatedAtDesc(
            Long campaignId, Long cabinetId, Pageable pageable);
}
