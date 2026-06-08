package ru.oparin.solution.service.campaign;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.oparin.solution.dto.analytics.manage.CampaignBudgetChartDto;
import ru.oparin.solution.model.CampaignBudgetTimeline;
import ru.oparin.solution.model.CampaignBudgetTimelineEventType;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Агрегация данных временной шкалы бюджета для биддер-графика.
 */
@Service
@RequiredArgsConstructor
public class CampaignBudgetChartService {

    private static final ZoneId ZONE = ZoneId.of("Europe/Moscow");
    private static final int DEFAULT_HOURS = 48;
    private static final int DEFAULT_STEP_HOURS = 2;

    private final CampaignBudgetTimelineService timelineService;

    @Transactional(readOnly = true)
    public CampaignBudgetChartDto buildChart(Long campaignId, Long cabinetId, Integer hours, Integer stepHours) {
        int periodHours = hours != null && hours > 0 ? hours : DEFAULT_HOURS;
        int bucketHours = stepHours != null && stepHours > 0 ? stepHours : DEFAULT_STEP_HOURS;
        LocalDateTime periodTo = LocalDateTime.now(ZONE);
        LocalDateTime periodFrom = periodTo.minusHours(periodHours);

        List<CampaignBudgetTimeline> events = timelineService.findInPeriod(campaignId, cabinetId, periodFrom, periodTo);

        List<CampaignBudgetChartDto.BudgetChartMarkerDto> markers = events.stream()
                .filter(e -> e.getEventType() != CampaignBudgetTimelineEventType.SNAPSHOT)
                .map(e -> CampaignBudgetChartDto.BudgetChartMarkerDto.builder()
                        .at(e.getRecordedAt())
                        .type(e.getEventType().name())
                        .amount(e.getEventType() == CampaignBudgetTimelineEventType.TOP_UP ? e.getTopUpAmount() : null)
                        .build())
                .toList();

        List<CampaignBudgetChartDto.BudgetChartIntervalDto> intervals = buildIntervals(events, periodFrom, periodTo);
        List<CampaignBudgetChartDto.BudgetChartPointDto> budgetPoints = buildBudgetPoints(events, periodFrom, periodTo, bucketHours);

        return CampaignBudgetChartDto.builder()
                .periodFrom(periodFrom)
                .periodTo(periodTo)
                .stepHours(bucketHours)
                .budgetPoints(budgetPoints)
                .intervals(intervals)
                .markers(markers)
                .build();
    }

    private List<CampaignBudgetChartDto.BudgetChartIntervalDto> buildIntervals(
            List<CampaignBudgetTimeline> events,
            LocalDateTime periodFrom,
            LocalDateTime periodTo
    ) {
        List<CampaignBudgetTimeline> statusEvents = events.stream()
                .filter(e -> e.getEventType() == CampaignBudgetTimelineEventType.START
                        || e.getEventType() == CampaignBudgetTimelineEventType.STOP)
                .sorted(Comparator.comparing(CampaignBudgetTimeline::getRecordedAt))
                .toList();

        List<CampaignBudgetChartDto.BudgetChartIntervalDto> intervals = new ArrayList<>();
        if (statusEvents.isEmpty()) {
            intervals.add(CampaignBudgetChartDto.BudgetChartIntervalDto.builder()
                    .from(periodFrom)
                    .to(periodTo)
                    .active(false)
                    .build());
            return intervals;
        }

        boolean active = false;
        LocalDateTime cursor = periodFrom;

        for (CampaignBudgetTimeline event : statusEvents) {
            if (event.getRecordedAt().isAfter(periodTo)) {
                break;
            }
            LocalDateTime eventAt = event.getRecordedAt().isBefore(periodFrom) ? periodFrom : event.getRecordedAt();
            if (eventAt.isAfter(cursor)) {
                intervals.add(CampaignBudgetChartDto.BudgetChartIntervalDto.builder()
                        .from(cursor)
                        .to(eventAt)
                        .active(active)
                        .build());
            }
            active = event.getEventType() == CampaignBudgetTimelineEventType.START;
            cursor = eventAt;
        }

        if (cursor.isBefore(periodTo)) {
            intervals.add(CampaignBudgetChartDto.BudgetChartIntervalDto.builder()
                    .from(cursor)
                    .to(periodTo)
                    .active(active)
                    .build());
        }
        return intervals;
    }

    private List<CampaignBudgetChartDto.BudgetChartPointDto> buildBudgetPoints(
            List<CampaignBudgetTimeline> events,
            LocalDateTime periodFrom,
            LocalDateTime periodTo,
            int stepHours
    ) {
        List<CampaignBudgetTimeline> budgetEvents = events.stream()
                .filter(e -> e.getBudgetTotal() != null
                        && (e.getEventType() == CampaignBudgetTimelineEventType.SNAPSHOT
                        || e.getEventType() == CampaignBudgetTimelineEventType.TOP_UP))
                .sorted(Comparator.comparing(CampaignBudgetTimeline::getRecordedAt))
                .toList();

        List<CampaignBudgetChartDto.BudgetChartPointDto> points = new ArrayList<>();
        int steps = (int) Math.ceil((double) (java.time.Duration.between(periodFrom, periodTo).toHours()) / stepHours);
        Integer lastKnown = null;

        for (int i = 0; i <= steps; i++) {
            LocalDateTime bucketEnd = periodFrom.plusHours((long) i * stepHours);
            if (bucketEnd.isAfter(periodTo)) {
                bucketEnd = periodTo;
            }
            LocalDateTime bucketAt = bucketEnd;

            for (CampaignBudgetTimeline event : budgetEvents) {
                if (!event.getRecordedAt().isAfter(bucketEnd)) {
                    lastKnown = event.getBudgetTotal();
                }
            }
            if (lastKnown != null) {
                points.add(CampaignBudgetChartDto.BudgetChartPointDto.builder()
                        .at(bucketAt)
                        .budgetRub(lastKnown)
                        .build());
            }
        }
        return points;
    }
}
