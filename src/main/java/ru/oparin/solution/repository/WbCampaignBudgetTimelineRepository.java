package ru.oparin.solution.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.oparin.solution.model.WbCampaignBudgetTimeline;
import ru.oparin.solution.model.WbCampaignBudgetTimelineEventType;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface WbCampaignBudgetTimelineRepository extends JpaRepository<WbCampaignBudgetTimeline, Long> {

    List<WbCampaignBudgetTimeline> findByCampaignIdAndCabinetIdAndRecordedAtBetweenOrderByRecordedAtAsc(
            Long campaignId, Long cabinetId, LocalDateTime from, LocalDateTime to);

    Optional<WbCampaignBudgetTimeline> findFirstByCampaignIdAndCabinetIdAndEventTypeInOrderByRecordedAtDesc(
            Long campaignId, Long cabinetId, List<WbCampaignBudgetTimelineEventType> eventTypes);

    /**
     * Последние события указанных типов строго до {@code before} (для якоря бюджета на левой границе графика).
     */
    List<WbCampaignBudgetTimeline> findByCampaignIdAndCabinetIdAndRecordedAtBeforeAndEventTypeInOrderByRecordedAtDesc(
            Long campaignId,
            Long cabinetId,
            LocalDateTime before,
            Collection<WbCampaignBudgetTimelineEventType> eventTypes,
            Pageable pageable);

    /** Последнее START/STOP до момента (активность РК на левой границе окна). */
    Optional<WbCampaignBudgetTimeline> findFirstByCampaignIdAndCabinetIdAndRecordedAtBeforeAndEventTypeInOrderByRecordedAtDesc(
            Long campaignId,
            Long cabinetId,
            LocalDateTime before,
            Collection<WbCampaignBudgetTimelineEventType> eventTypes);
}
