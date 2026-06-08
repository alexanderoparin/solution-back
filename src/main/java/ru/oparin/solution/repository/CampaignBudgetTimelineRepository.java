package ru.oparin.solution.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.oparin.solution.model.CampaignBudgetTimeline;
import ru.oparin.solution.model.CampaignBudgetTimelineEventType;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface CampaignBudgetTimelineRepository extends JpaRepository<CampaignBudgetTimeline, Long> {

    List<CampaignBudgetTimeline> findByCampaignIdAndCabinetIdAndRecordedAtBetweenOrderByRecordedAtAsc(
            Long campaignId, Long cabinetId, LocalDateTime from, LocalDateTime to);

    Optional<CampaignBudgetTimeline> findFirstByCampaignIdAndCabinetIdAndEventTypeInOrderByRecordedAtDesc(
            Long campaignId, Long cabinetId, List<CampaignBudgetTimelineEventType> eventTypes);
}
