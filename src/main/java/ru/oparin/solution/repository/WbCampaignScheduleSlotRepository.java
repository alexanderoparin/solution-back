package ru.oparin.solution.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.oparin.solution.model.WbCampaignScheduleSlot;

import java.util.List;
import java.util.UUID;

public interface WbCampaignScheduleSlotRepository extends JpaRepository<WbCampaignScheduleSlot, Long> {

    List<WbCampaignScheduleSlot> findByCampaignIdAndCabinetIdOrderByDayOfWeekAscStartTimeAsc(
            Long campaignId, Long cabinetId);

    List<WbCampaignScheduleSlot> findByRepeatGroupId(UUID repeatGroupId);

    void deleteByCampaignIdAndCabinetId(Long campaignId, Long cabinetId);

    List<WbCampaignScheduleSlot> findByCabinetId(Long cabinetId);
}
