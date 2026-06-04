package ru.oparin.solution.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.oparin.solution.model.CampaignScheduleSlot;

import java.util.List;
import java.util.UUID;

public interface CampaignScheduleSlotRepository extends JpaRepository<CampaignScheduleSlot, Long> {

    List<CampaignScheduleSlot> findByCampaignIdAndCabinetIdOrderByDayOfWeekAscStartTimeAsc(
            Long campaignId, Long cabinetId);

    List<CampaignScheduleSlot> findByRepeatGroupId(UUID repeatGroupId);

    void deleteByCampaignIdAndCabinetId(Long campaignId, Long cabinetId);

    List<CampaignScheduleSlot> findByCabinetId(Long cabinetId);
}
