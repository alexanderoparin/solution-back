package ru.oparin.solution.service.campaign;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.oparin.solution.model.WbCampaignBudgetTimeline;
import ru.oparin.solution.model.WbCampaignBudgetTimelineEventType;
import ru.oparin.solution.repository.WbCampaignBudgetTimelineRepository;

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
public class WbCampaignBudgetTimelineService {

    private static final ZoneId ZONE = ZoneId.of("Europe/Moscow");
    private static final int CHART_BUDGET_ANCHOR_LIMIT = 150;

    private final WbCampaignBudgetTimelineRepository timelineRepository;

    @Transactional
    public void recordSnapshot(Long campaignId, Long cabinetId, Integer budgetTotal) {
        if (budgetTotal == null) {
            return;
        }
        timelineRepository.save(WbCampaignBudgetTimeline.builder()
                .campaignId(campaignId)
                .cabinetId(cabinetId)
                .recordedAt(LocalDateTime.now(ZONE))
                .eventType(WbCampaignBudgetTimelineEventType.SNAPSHOT)
                .budgetTotal(budgetTotal)
                .build());
    }

    @Transactional
    public void recordTopUp(Long campaignId, Long cabinetId, int topUpAmount, Integer budgetTotalAfter) {
        timelineRepository.save(WbCampaignBudgetTimeline.builder()
                .campaignId(campaignId)
                .cabinetId(cabinetId)
                .recordedAt(LocalDateTime.now(ZONE))
                .eventType(WbCampaignBudgetTimelineEventType.TOP_UP)
                .topUpAmount(topUpAmount)
                .budgetTotal(budgetTotalAfter)
                .build());
    }

    @Transactional
    public void recordStart(Long campaignId, Long cabinetId) {
        timelineRepository.save(WbCampaignBudgetTimeline.builder()
                .campaignId(campaignId)
                .cabinetId(cabinetId)
                .recordedAt(LocalDateTime.now(ZONE))
                .eventType(WbCampaignBudgetTimelineEventType.START)
                .build());
    }

    @Transactional
    public void recordStop(Long campaignId, Long cabinetId) {
        timelineRepository.save(WbCampaignBudgetTimeline.builder()
                .campaignId(campaignId)
                .cabinetId(cabinetId)
                .recordedAt(LocalDateTime.now(ZONE))
                .eventType(WbCampaignBudgetTimelineEventType.STOP)
                .build());
    }

    @Transactional(readOnly = true)
    public List<WbCampaignBudgetTimeline> findInPeriod(Long campaignId, Long cabinetId, LocalDateTime from, LocalDateTime to) {
        return timelineRepository.findByCampaignIdAndCabinetIdAndRecordedAtBetweenOrderByRecordedAtAsc(
                campaignId, cabinetId, from, to);
    }

    /**
     * SNAPSHOT/TOP_UP до начала окна графика (хронологический порядок) для расчёта остатка на {@code periodFrom}.
     */
    @Transactional(readOnly = true)
    public List<WbCampaignBudgetTimeline> findBudgetAnchorBefore(
            Long campaignId,
            Long cabinetId,
            LocalDateTime before
    ) {
        List<WbCampaignBudgetTimeline> recent = timelineRepository
                .findByCampaignIdAndCabinetIdAndRecordedAtBeforeAndEventTypeInOrderByRecordedAtDesc(
                        campaignId,
                        cabinetId,
                        before,
                        List.of(WbCampaignBudgetTimelineEventType.SNAPSHOT, WbCampaignBudgetTimelineEventType.TOP_UP),
                        PageRequest.of(0, CHART_BUDGET_ANCHOR_LIMIT));
        List<WbCampaignBudgetTimeline> ascending = new ArrayList<>(recent);
        ascending.sort(Comparator.comparing(WbCampaignBudgetTimeline::getRecordedAt));
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
                        List.of(WbCampaignBudgetTimelineEventType.START, WbCampaignBudgetTimelineEventType.STOP))
                .map(event -> event.getEventType() == WbCampaignBudgetTimelineEventType.START)
                .orElse(false);
    }
}
