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
 * Точки линии бюджета строятся по фактическим SNAPSHOT/TOP_UP без часовой агрегации.
 */
@Service
@RequiredArgsConstructor
public class CampaignBudgetChartService {

    private static final ZoneId ZONE = ZoneId.of("Europe/Moscow");
    private static final int DEFAULT_HOURS = 48;

    private final CampaignBudgetTimelineService timelineService;

    @Transactional(readOnly = true)
    public CampaignBudgetChartDto buildChart(Long campaignId, Long cabinetId, Integer hours, Integer ignoredStepHours) {
        int periodHours = hours != null && hours > 0 ? hours : DEFAULT_HOURS;
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
        List<CampaignBudgetChartDto.BudgetChartPointDto> budgetPoints = buildBudgetPointsFromEvents(events, periodFrom, periodTo);

        return CampaignBudgetChartDto.builder()
                .periodFrom(periodFrom)
                .periodTo(periodTo)
                .stepHours(0)
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

    /**
     * Точки линии бюджета по фактическим SNAPSHOT/TOP_UP из timeline (без часовой агрегации).
     */
    private List<CampaignBudgetChartDto.BudgetChartPointDto> buildBudgetPointsFromEvents(
            List<CampaignBudgetTimeline> events,
            LocalDateTime periodFrom,
            LocalDateTime periodTo
    ) {
        List<CampaignBudgetTimeline> budgetEvents = events.stream()
                .filter(e -> e.getEventType() == CampaignBudgetTimelineEventType.SNAPSHOT
                        || (e.getEventType() == CampaignBudgetTimelineEventType.TOP_UP
                        && (e.getBudgetTotal() != null || e.getTopUpAmount() != null)))
                .sorted(Comparator.comparing(CampaignBudgetTimeline::getRecordedAt))
                .toList();

        if (budgetEvents.isEmpty()) {
            return List.of();
        }

        Integer budgetAtPeriodStart = resolveBudgetAt(events, periodFrom);

        List<CampaignBudgetChartDto.BudgetChartPointDto> points = new ArrayList<>();
        if (budgetAtPeriodStart != null) {
            points.add(CampaignBudgetChartDto.BudgetChartPointDto.builder()
                    .at(periodFrom)
                    .budgetRub(budgetAtPeriodStart)
                    .build());
        }

        Integer lastBudget = budgetAtPeriodStart;
        for (CampaignBudgetTimeline event : budgetEvents) {
            LocalDateTime at = event.getRecordedAt();
            if (at.isBefore(periodFrom) || at.isAfter(periodTo)) {
                if (!at.isAfter(periodTo)) {
                    lastBudget = resolveEventBudgetRub(event, lastBudget);
                }
                continue;
            }
            int eventBudget = resolveEventBudgetRub(event, lastBudget);
            if (event.getEventType() == CampaignBudgetTimelineEventType.TOP_UP
                    && lastBudget != null
                    && eventBudget != lastBudget) {
                LocalDateTime beforeTopUp = at.minusNanos(1_000_000L);
                if (beforeTopUp.isBefore(periodFrom)) {
                    beforeTopUp = periodFrom;
                }
                points.add(CampaignBudgetChartDto.BudgetChartPointDto.builder()
                        .at(beforeTopUp)
                        .budgetRub(lastBudget)
                        .build());
            }
            points.add(CampaignBudgetChartDto.BudgetChartPointDto.builder()
                    .at(at)
                    .budgetRub(eventBudget)
                    .build());
            lastBudget = eventBudget;
        }

        applyPausePlateaus(events, points, periodFrom, periodTo);

        Integer lastKnown = resolveBudgetAt(events, periodTo);
        if (lastKnown != null) {
            boolean endsAtPeriodTo = !points.isEmpty()
                    && points.get(points.size() - 1).getAt().equals(periodTo);
            if (!endsAtPeriodTo) {
                points.add(CampaignBudgetChartDto.BudgetChartPointDto.builder()
                        .at(periodTo)
                        .budgetRub(lastKnown)
                        .build());
            }
        }
        points.sort(Comparator.comparing(CampaignBudgetChartDto.BudgetChartPointDto::getAt));
        return simplifyBudgetPoints(points);
    }

    /**
     * Горизонталь после trail (5 мин после STOP) до следующего START.
     */
    private void applyPausePlateaus(
            List<CampaignBudgetTimeline> events,
            List<CampaignBudgetChartDto.BudgetChartPointDto> points,
            LocalDateTime periodFrom,
            LocalDateTime periodTo
    ) {
        List<CampaignBudgetTimeline> stops = events.stream()
                .filter(e -> e.getEventType() == CampaignBudgetTimelineEventType.STOP)
                .sorted(Comparator.comparing(CampaignBudgetTimeline::getRecordedAt))
                .toList();
        if (stops.isEmpty()) {
            return;
        }

        List<LocalDateTime> starts = events.stream()
                .filter(e -> e.getEventType() == CampaignBudgetTimelineEventType.START)
                .map(CampaignBudgetTimeline::getRecordedAt)
                .sorted()
                .toList();

        for (CampaignBudgetTimeline stop : stops) {
            LocalDateTime stopAt = stop.getRecordedAt();
            if (stopAt.isAfter(periodTo)) {
                continue;
            }
            LocalDateTime trailEnd = CampaignBudgetTrailSupport.trailEndAfter(stopAt);
            LocalDateTime plateauFrom = trailEnd.isBefore(periodFrom) ? periodFrom : trailEnd;
            if (plateauFrom.isAfter(periodTo)) {
                continue;
            }

            LocalDateTime nextStart = starts.stream()
                    .filter(at -> at.isAfter(stopAt))
                    .findFirst()
                    .orElse(null);

            LocalDateTime plateauTo;
            if (nextStart != null && !nextStart.isAfter(periodTo)) {
                plateauTo = nextStart.minusNanos(1_000_000L);
            } else {
                plateauTo = periodTo;
            }
            if (!plateauFrom.isBefore(plateauTo)) {
                continue;
            }

            Integer plateauBudget = resolveBudgetAt(events, plateauFrom);
            if (plateauBudget == null) {
                continue;
            }
            points.add(CampaignBudgetChartDto.BudgetChartPointDto.builder()
                    .at(plateauFrom)
                    .budgetRub(plateauBudget)
                    .build());
            points.add(CampaignBudgetChartDto.BudgetChartPointDto.builder()
                    .at(plateauTo)
                    .budgetRub(plateauBudget)
                    .build());
        }
    }

    private Integer resolveBudgetAt(List<CampaignBudgetTimeline> events, LocalDateTime at) {
        List<CampaignBudgetTimeline> budgetEvents = events.stream()
                .filter(e -> e.getBudgetTotal() != null
                        || (e.getEventType() == CampaignBudgetTimelineEventType.TOP_UP && e.getTopUpAmount() != null))
                .filter(e -> !e.getRecordedAt().isAfter(at))
                .sorted(Comparator.comparing(CampaignBudgetTimeline::getRecordedAt))
                .toList();
        Integer lastBudget = null;
        for (CampaignBudgetTimeline event : budgetEvents) {
            if (event.getEventType() == CampaignBudgetTimelineEventType.SNAPSHOT
                    || event.getEventType() == CampaignBudgetTimelineEventType.TOP_UP) {
                lastBudget = resolveEventBudgetRub(event, lastBudget);
            }
        }
        return lastBudget;
    }

    /**
     * Для TOP_UP учитывает устаревший budget_total от WB (max с оценкой до+сумма).
     */
    private int resolveEventBudgetRub(CampaignBudgetTimeline event, Integer budgetBefore) {
        if (event.getEventType() != CampaignBudgetTimelineEventType.TOP_UP
                || event.getTopUpAmount() == null) {
            return event.getBudgetTotal() != null ? event.getBudgetTotal() : 0;
        }
        int estimated = (budgetBefore != null ? budgetBefore : 0) + event.getTopUpAmount();
        if (event.getBudgetTotal() == null) {
            return estimated;
        }
        return Math.max(event.getBudgetTotal(), estimated);
    }

    /**
     * Убирает подряд идущие точки с одинаковым остатком — линия не «ломается» на горизонтальных ступеньках.
     */
    private List<CampaignBudgetChartDto.BudgetChartPointDto> simplifyBudgetPoints(
            List<CampaignBudgetChartDto.BudgetChartPointDto> points
    ) {
        if (points.size() <= 2) {
            return points;
        }
        List<CampaignBudgetChartDto.BudgetChartPointDto> simplified = new ArrayList<>();
        simplified.add(points.get(0));
        for (int i = 1; i < points.size(); i++) {
            CampaignBudgetChartDto.BudgetChartPointDto current = points.get(i);
            CampaignBudgetChartDto.BudgetChartPointDto previous = simplified.get(simplified.size() - 1);
            if (!java.util.Objects.equals(current.getBudgetRub(), previous.getBudgetRub())) {
                simplified.add(current);
            }
        }
        CampaignBudgetChartDto.BudgetChartPointDto lastInput = points.get(points.size() - 1);
        CampaignBudgetChartDto.BudgetChartPointDto lastKept = simplified.get(simplified.size() - 1);
        if (!lastKept.getAt().equals(lastInput.getAt())) {
            simplified.add(lastInput);
        }
        return simplified;
    }
}
