package ru.oparin.solution.service.hypothesis;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.oparin.solution.dto.hypothesis.WbHypothesisCriterionValueDto;
import ru.oparin.solution.model.WbHypothesisCriterion;
import ru.oparin.solution.model.WbProductCardAnalytics;
import ru.oparin.solution.model.WbPromotionCampaignStatistics;
import ru.oparin.solution.model.WbPromotionNormQueryStatistics;
import ru.oparin.solution.repository.WbProductCardAnalyticsRepository;
import ru.oparin.solution.repository.WbPromotionCampaignStatisticsRepository;
import ru.oparin.solution.repository.WbPromotionNormQueryStatisticsRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Агрегация метрик артикула за период для гипотез.
 */
@Service
@RequiredArgsConstructor
public class WbHypothesisMetricsService {

    private static final int SCALE = 4;

    private final WbProductCardAnalyticsRepository productCardAnalyticsRepository;
    private final WbPromotionCampaignStatisticsRepository campaignStatisticsRepository;
    private final WbPromotionNormQueryStatisticsRepository normQueryStatisticsRepository;

    /**
     * Считает значения выбранных критериев: baseline vs check.
     */
    @Transactional(readOnly = true)
    public List<WbHypothesisCriterionValueDto> compareCriteria(
            Long cabinetId,
            Long nmId,
            LocalDate baselineFrom,
            LocalDate baselineTo,
            LocalDate checkFrom,
            LocalDate checkTo,
            List<WbHypothesisCriterion> criteria
    ) {
        Map<WbHypothesisCriterion, BigDecimal> baseline = aggregatePeriod(cabinetId, nmId, baselineFrom, baselineTo);
        Map<WbHypothesisCriterion, BigDecimal> check = aggregatePeriod(cabinetId, nmId, checkFrom, checkTo);
        List<WbHypothesisCriterionValueDto> rows = new ArrayList<>();
        for (WbHypothesisCriterion criterion : criteria) {
            BigDecimal baseVal = baseline.get(criterion);
            BigDecimal checkVal = check.get(criterion);
            BigDecimal change = null;
            BigDecimal changePercent = null;
            Boolean improved = null;
            if (baseVal != null && checkVal != null) {
                change = checkVal.subtract(baseVal);
                if (baseVal.compareTo(BigDecimal.ZERO) != 0) {
                    changePercent = change
                            .multiply(BigDecimal.valueOf(100))
                            .divide(baseVal.abs(), SCALE, RoundingMode.HALF_UP);
                }
                int cmp = checkVal.compareTo(baseVal);
                if (cmp == 0) {
                    improved = null;
                } else if (criterion.isLowerIsBetter()) {
                    improved = cmp < 0;
                } else {
                    improved = cmp > 0;
                }
            }
            rows.add(WbHypothesisCriterionValueDto.builder()
                    .key(criterion.getKey())
                    .label(criterion.getLabelRu())
                    .baseline(baseVal)
                    .check(checkVal)
                    .change(change)
                    .changePercent(changePercent)
                    .improved(improved)
                    .lowerIsBetter(criterion.isLowerIsBetter())
                    .build());
        }
        return rows;
    }

    /**
     * Агрегаты по дням для графика динамики (baseline + check).
     */
    @Transactional(readOnly = true)
    public List<WbHypothesisDailyPoint> dailySeries(
            Long cabinetId,
            Long nmId,
            LocalDate from,
            LocalDate to,
            List<WbHypothesisCriterion> criteria
    ) {
        List<WbHypothesisDailyPoint> points = new ArrayList<>();
        LocalDate cursor = from;
        while (!cursor.isAfter(to)) {
            Map<WbHypothesisCriterion, BigDecimal> day = aggregatePeriod(cabinetId, nmId, cursor, cursor);
            points.add(new WbHypothesisDailyPoint(cursor, day));
            cursor = cursor.plusDays(1);
        }
        return points;
    }

    private Map<WbHypothesisCriterion, BigDecimal> aggregatePeriod(
            Long cabinetId,
            Long nmId,
            LocalDate from,
            LocalDate to
    ) {
        List<WbProductCardAnalytics> funnel = productCardAnalyticsRepository
                .findByCabinet_IdAndProductCardNmIdAndDateBetween(cabinetId, nmId, from, to);
        List<WbPromotionCampaignStatistics> ads = campaignStatisticsRepository
                .findByCabinetIdAndNmIdAndDateBetween(cabinetId, nmId, from, to);
        List<WbPromotionNormQueryStatistics> positions = normQueryStatisticsRepository
                .findByCabinetIdAndNmIdAndDateBetweenWithAvgPos(cabinetId, nmId, from, to);

        long orders = 0;
        BigDecimal ordersSum = BigDecimal.ZERO;
        long buyouts = 0;
        BigDecimal buyoutPercentSum = BigDecimal.ZERO;
        int buyoutPercentDays = 0;

        for (WbProductCardAnalytics row : funnel) {
            orders += nullToZero(row.getOrders());
            if (row.getOrdersSum() != null) {
                ordersSum = ordersSum.add(row.getOrdersSum());
            }
            buyouts += nullToZero(row.getBuyouts());
            if (row.getBuyoutPercent() != null) {
                buyoutPercentSum = buyoutPercentSum.add(row.getBuyoutPercent());
                buyoutPercentDays++;
            }
        }

        long views = 0;
        long clicks = 0;
        BigDecimal costs = BigDecimal.ZERO;
        for (WbPromotionCampaignStatistics row : ads) {
            views += nullToZero(row.getViews());
            clicks += nullToZero(row.getClicks());
            if (row.getSum() != null) {
                costs = costs.add(row.getSum());
            }
        }

        BigDecimal avgPos = null;
        BigDecimal posWeight = BigDecimal.ZERO;
        BigDecimal posAcc = BigDecimal.ZERO;
        long posClicks = 0;
        for (WbPromotionNormQueryStatistics row : positions) {
            if (row.getAvgPos() == null) {
                continue;
            }
            int rowClicks = nullToZero(row.getClicks());
            if (rowClicks > 0) {
                posAcc = posAcc.add(row.getAvgPos().multiply(BigDecimal.valueOf(rowClicks)));
                posClicks += rowClicks;
            } else {
                posAcc = posAcc.add(row.getAvgPos());
                posWeight = posWeight.add(BigDecimal.ONE);
            }
        }
        if (posClicks > 0) {
            avgPos = posAcc.divide(BigDecimal.valueOf(posClicks), SCALE, RoundingMode.HALF_UP);
        } else if (posWeight.compareTo(BigDecimal.ZERO) > 0) {
            avgPos = posAcc.divide(posWeight, SCALE, RoundingMode.HALF_UP);
        }

        Map<WbHypothesisCriterion, BigDecimal> map = new EnumMap<>(WbHypothesisCriterion.class);
        map.put(WbHypothesisCriterion.ORDERS, BigDecimal.valueOf(orders));
        map.put(WbHypothesisCriterion.VIEWS, BigDecimal.valueOf(views));

        if (views > 0) {
            map.put(
                    WbHypothesisCriterion.CTR,
                    BigDecimal.valueOf(clicks)
                            .multiply(BigDecimal.valueOf(100))
                            .divide(BigDecimal.valueOf(views), SCALE, RoundingMode.HALF_UP)
            );
        }
        if (clicks > 0) {
            map.put(
                    WbHypothesisCriterion.CPC,
                    costs.divide(BigDecimal.valueOf(clicks), SCALE, RoundingMode.HALF_UP)
            );
        }
        if (orders > 0) {
            map.put(
                    WbHypothesisCriterion.CPO,
                    costs.divide(BigDecimal.valueOf(orders), SCALE, RoundingMode.HALF_UP)
            );
        }
        if (ordersSum.compareTo(BigDecimal.ZERO) > 0) {
            map.put(
                    WbHypothesisCriterion.DRR,
                    costs.multiply(BigDecimal.valueOf(100))
                            .divide(ordersSum, SCALE, RoundingMode.HALF_UP)
            );
        }
        if (orders > 0 && buyouts > 0) {
            map.put(
                    WbHypothesisCriterion.BUYOUT_PERCENT,
                    BigDecimal.valueOf(buyouts)
                            .multiply(BigDecimal.valueOf(100))
                            .divide(BigDecimal.valueOf(orders), SCALE, RoundingMode.HALF_UP)
            );
        } else if (buyoutPercentDays > 0) {
            map.put(
                    WbHypothesisCriterion.BUYOUT_PERCENT,
                    buyoutPercentSum.divide(BigDecimal.valueOf(buyoutPercentDays), SCALE, RoundingMode.HALF_UP)
            );
        }
        if (avgPos != null) {
            map.put(WbHypothesisCriterion.AVG_POS, avgPos);
        }
        return map;
    }

    private static int nullToZero(Integer value) {
        return value == null ? 0 : value;
    }

    /**
     * Точка дневного ряда для графика.
     */
    public record WbHypothesisDailyPoint(LocalDate date, Map<WbHypothesisCriterion, BigDecimal> values) {
    }
}
