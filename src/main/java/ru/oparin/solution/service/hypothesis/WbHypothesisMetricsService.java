package ru.oparin.solution.service.hypothesis;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.oparin.solution.dto.hypothesis.WbHypothesisCriterionValueDto;
import ru.oparin.solution.model.*;
import ru.oparin.solution.repository.WbProductCardAnalyticsRepository;
import ru.oparin.solution.repository.WbProductPriceHistoryRepository;
import ru.oparin.solution.repository.WbPromotionCampaignStatisticsRepository;
import ru.oparin.solution.repository.WbPromotionNormQueryStatisticsRepository;
import ru.oparin.solution.service.analytics.MathUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;

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
    private final WbProductPriceHistoryRepository productPriceHistoryRepository;

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
        long openCard = 0;
        long addToCart = 0;
        BigDecimal ordersSum = BigDecimal.ZERO;
        for (WbProductCardAnalytics row : funnel) {
            orders += nullToZero(row.getOrders());
            openCard += nullToZero(row.getOpenCard());
            addToCart += nullToZero(row.getAddToCart());
            if (row.getOrdersSum() != null) {
                ordersSum = ordersSum.add(row.getOrdersSum());
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
        // Нет строк аналитики за период = нет данных (null), а не ноль.
        // Иначе «пустая» выгрузка выглядит как ухудшение (0 ↓ -N).
        if (!funnel.isEmpty()) {
            map.put(WbHypothesisCriterion.ORDERS, BigDecimal.valueOf(orders));
        }
        if (!ads.isEmpty()) {
            map.put(WbHypothesisCriterion.VIEWS, BigDecimal.valueOf(views));
        }

        if (!ads.isEmpty() && views > 0) {
            map.put(
                    WbHypothesisCriterion.CTR,
                    BigDecimal.valueOf(clicks)
                            .multiply(BigDecimal.valueOf(100))
                            .divide(BigDecimal.valueOf(views), SCALE, RoundingMode.HALF_UP)
            );
        }
        if (!ads.isEmpty() && clicks > 0) {
            map.put(
                    WbHypothesisCriterion.CPC,
                    costs.divide(BigDecimal.valueOf(clicks), SCALE, RoundingMode.HALF_UP)
            );
        }
        if (!funnel.isEmpty() && orders > 0) {
            map.put(
                    WbHypothesisCriterion.CPO,
                    costs.divide(BigDecimal.valueOf(orders), SCALE, RoundingMode.HALF_UP)
            );
        }
        if (!funnel.isEmpty() && ordersSum.compareTo(BigDecimal.ZERO) > 0) {
            map.put(
                    WbHypothesisCriterion.DRR,
                    costs.multiply(BigDecimal.valueOf(100))
                            .divide(ordersSum, SCALE, RoundingMode.HALF_UP)
            );
        }
        if (avgPos != null) {
            map.put(WbHypothesisCriterion.AVG_POS, avgPos);
        }
        if (!funnel.isEmpty()) {
            BigDecimal cartConversion = MathUtils.calculatePercentage(addToCart, openCard);
            if (cartConversion != null) {
                map.put(WbHypothesisCriterion.CART_CONVERSION, cartConversion);
            }
            BigDecimal orderConversion = MathUtils.calculatePercentage(orders, addToCart);
            if (orderConversion != null) {
                map.put(WbHypothesisCriterion.ORDER_CONVERSION, orderConversion);
            }
        }
        BigDecimal spp = averageSpp(cabinetId, nmId, from, to);
        if (spp != null) {
            map.put(WbHypothesisCriterion.SPP, spp);
        }
        return map;
    }

    /**
     * Средний СПП (%) за период по истории цен: по каждому дню берётся запись без sizeId, иначе первая с СПП.
     */
    private BigDecimal averageSpp(Long cabinetId, Long nmId, LocalDate from, LocalDate to) {
        List<WbProductPriceHistory> prices = productPriceHistoryRepository
                .findByNmIdAndDateBetweenAndCabinet_Id(nmId, from, to, cabinetId);
        if (prices.isEmpty()) {
            return null;
        }
        Map<LocalDate, List<WbProductPriceHistory>> byDate = new LinkedHashMap<>();
        for (WbProductPriceHistory row : prices) {
            byDate.computeIfAbsent(row.getDate(), ignored -> new ArrayList<>()).add(row);
        }
        BigDecimal sum = BigDecimal.ZERO;
        int days = 0;
        for (List<WbProductPriceHistory> dayRows : byDate.values()) {
            Integer spp = pickDaySpp(dayRows);
            if (spp == null) {
                continue;
            }
            sum = sum.add(BigDecimal.valueOf(spp));
            days++;
        }
        if (days == 0) {
            return null;
        }
        return sum.divide(BigDecimal.valueOf(days), SCALE, RoundingMode.HALF_UP);
    }

    private static Integer pickDaySpp(List<WbProductPriceHistory> dayRows) {
        for (WbProductPriceHistory row : dayRows) {
            if (row.getSizeId() == null && row.getSppDiscount() != null) {
                return row.getSppDiscount();
            }
        }
        for (WbProductPriceHistory row : dayRows) {
            if (row.getSppDiscount() != null) {
                return row.getSppDiscount();
            }
        }
        return null;
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
