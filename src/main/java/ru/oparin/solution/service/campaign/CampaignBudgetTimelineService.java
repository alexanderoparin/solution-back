package ru.oparin.solution.service.campaign;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.oparin.solution.model.CampaignBudgetTimeline;
import ru.oparin.solution.model.CampaignBudgetTimelineEventType;
import ru.oparin.solution.repository.CampaignBudgetTimelineRepository;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

/**
 * Запись событий временной шкалы бюджета рекламной кампании.
 */
@Service
@RequiredArgsConstructor
public class CampaignBudgetTimelineService {

    private static final ZoneId ZONE = ZoneId.of("Europe/Moscow");

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
}
