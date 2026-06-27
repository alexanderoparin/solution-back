package ru.oparin.solution.service.campaign;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.oparin.solution.model.CampaignBudgetTimeline;
import ru.oparin.solution.model.CampaignBudgetTimelineEventType;
import ru.oparin.solution.repository.CampaignBudgetTimelineRepository;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Запись событий временной шкалы бюджета рекламной кампании.
 */
@Service
@RequiredArgsConstructor
public class CampaignBudgetTimelineService {

    private static final ZoneId ZONE = ZoneId.of("Europe/Moscow");
    private static final int CHART_BUDGET_ANCHOR_LIMIT = 150;

    private final CampaignBudgetTimelineRepository timelineRepository;

    @Transactional
    public void recordSnapshot(Long campaignId, Long cabinetId, Integer budgetTotal) {
        if (budgetTotal == null) {
            return;
        }
        timelineRepository.save(CampaignBudgetTimeline.builder()
                .campaignId(campaignId)
                .cabinetId(cabinetId)
                .recordedAt(LocalDateTime.now(ZONE))
                .eventType(CampaignBudgetTimelineEventType.SNAPSHOT)
                .budgetTotal(budgetTotal)
                .build());
    }

    @Transactional
    public void recordTopUp(Long campaignId, Long cabinetId, int topUpAmount, Integer budgetTotalAfter) {
        timelineRepository.save(CampaignBudgetTimeline.builder()
                .campaignId(campaignId)
                .cabinetId(cabinetId)
                .recordedAt(LocalDateTime.now(ZONE))
                .eventType(CampaignBudgetTimelineEventType.TOP_UP)
                .topUpAmount(topUpAmount)
                .budgetTotal(budgetTotalAfter)
                .build());
    }

    @Transactional
    public void recordStart(Long campaignId, Long cabinetId) {
        timelineRepository.save(CampaignBudgetTimeline.builder()
                .campaignId(campaignId)
                .cabinetId(cabinetId)
                .recordedAt(LocalDateTime.now(ZONE))
                .eventType(CampaignBudgetTimelineEventType.START)
                .build());
    }

    @Transactional
    public void recordStop(Long campaignId, Long cabinetId) {
        timelineRepository.save(CampaignBudgetTimeline.builder()
                .campaignId(campaignId)
                .cabinetId(cabinetId)
                .recordedAt(LocalDateTime.now(ZONE))
                .eventType(CampaignBudgetTimelineEventType.STOP)
                .build());
    }

    @Transactional(readOnly = true)
    public List<CampaignBudgetTimeline> findInPeriod(Long campaignId, Long cabinetId, LocalDateTime from, LocalDateTime to) {
        return timelineRepository.findByCampaignIdAndCabinetIdAndRecordedAtBetweenOrderByRecordedAtAsc(
                campaignId, cabinetId, from, to);
    }

    /**
     * SNAPSHOT/TOP_UP до начала окна графика (хронологический порядок) для расчёта остатка на {@code periodFrom}.
     */
    @Transactional(readOnly = true)
    public List<CampaignBudgetTimeline> findBudgetAnchorBefore(
            Long campaignId,
            Long cabinetId,
            LocalDateTime before
    ) {
        List<CampaignBudgetTimeline> recent = timelineRepository
                .findByCampaignIdAndCabinetIdAndRecordedAtBeforeAndEventTypeInOrderByRecordedAtDesc(
                        campaignId,
                        cabinetId,
                        before,
                        List.of(CampaignBudgetTimelineEventType.SNAPSHOT, CampaignBudgetTimelineEventType.TOP_UP),
                        PageRequest.of(0, CHART_BUDGET_ANCHOR_LIMIT));
        List<CampaignBudgetTimeline> ascending = new ArrayList<>(recent);
        ascending.sort(Comparator.comparing(CampaignBudgetTimeline::getRecordedAt));
        return ascending;
    }

    /**
     * {@code true}, если на момент {@code before} РК была активна (последнее событие — START).
     */
    @Transactional(readOnly = true)
    public boolean wasActiveImmediatelyBefore(Long campaignId, Long cabinetId, LocalDateTime before) {
        return timelineRepository
                .findFirstByCampaignIdAndCabinetIdAndRecordedAtBeforeAndEventTypeInOrderByRecordedAtDesc(
                        campaignId,
                        cabinetId,
                        before,
                        List.of(CampaignBudgetTimelineEventType.START, CampaignBudgetTimelineEventType.STOP))
                .map(event -> event.getEventType() == CampaignBudgetTimelineEventType.START)
                .orElse(false);
    }
}
