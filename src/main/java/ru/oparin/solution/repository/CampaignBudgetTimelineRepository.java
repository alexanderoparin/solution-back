package ru.oparin.solution.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.oparin.solution.model.CampaignBudgetTimeline;
import ru.oparin.solution.model.CampaignBudgetTimelineEventType;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface CampaignBudgetTimelineRepository extends JpaRepository<CampaignBudgetTimeline, Long> {

    List<CampaignBudgetTimeline> findByCampaignIdAndCabinetIdAndRecordedAtBetweenOrderByRecordedAtAsc(
            Long campaignId, Long cabinetId, LocalDateTime from, LocalDateTime to);

    Optional<CampaignBudgetTimeline> findFirstByCampaignIdAndCabinetIdAndEventTypeInOrderByRecordedAtDesc(
            Long campaignId, Long cabinetId, List<CampaignBudgetTimelineEventType> eventTypes);

    /**
     * Последние события указанных типов строго до {@code before} (для якоря бюджета на левой границе графика).
     */
    List<CampaignBudgetTimeline> findByCampaignIdAndCabinetIdAndRecordedAtBeforeAndEventTypeInOrderByRecordedAtDesc(
            Long campaignId,
            Long cabinetId,
            LocalDateTime before,
            Collection<CampaignBudgetTimelineEventType> eventTypes,
            Pageable pageable);

    /** Последнее START/STOP до момента (активность РК на левой границе окна). */
    Optional<CampaignBudgetTimeline> findFirstByCampaignIdAndCabinetIdAndRecordedAtBeforeAndEventTypeInOrderByRecordedAtDesc(
            Long campaignId,
            Long cabinetId,
            LocalDateTime before,
            Collection<CampaignBudgetTimelineEventType> eventTypes);
}
