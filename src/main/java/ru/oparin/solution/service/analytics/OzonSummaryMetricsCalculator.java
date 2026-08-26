package ru.oparin.solution.service.analytics;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.oparin.solution.dto.analytics.AggregatedMetricsDto;
import ru.oparin.solution.dto.analytics.PeriodDto;
import ru.oparin.solution.model.OzonProductCard;
import ru.oparin.solution.model.OzonProductCardAnalytics;
import ru.oparin.solution.model.OzonPromotionCampaignProductStatistics;
import ru.oparin.solution.repository.OzonPromotionCampaignProductStatisticsRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;

/**
 * Агрегация метрик сводной аналитики Ozon (воронка Seller API + реклама Performance).
 */
@Component
@RequiredArgsConstructor
public class OzonSummaryMetricsCalculator {

    private final OzonPromotionCampaignProductStatisticsRepository productStatisticsRepository;

    /**
     * Сводные метрики по периодам для набора product_id.
     */
    public Map<Integer, AggregatedMetricsDto> calculateAggregatedMetrics(
            Long cabinetId,
            Set<Long> productIds,
            Map<Long, List<OzonProductCardAnalytics>> analyticsByProductId,
            List<PeriodDto> periods
    ) {
        Map<Integer, AggregatedMetricsDto> result = new HashMap<>();
        if (periods.isEmpty()) {
            return result;
        }
        Set<Long> skus = collectSkus(analyticsByProductId, productIds);
        LocalDate minFrom = periods.stream().map(PeriodDto::getDateFrom).min(LocalDate::compareTo).orElseThrow();
        LocalDate maxTo = periods.stream().map(PeriodDto::getDateTo).max(LocalDate::compareTo).orElseThrow();
        List<OzonPromotionCampaignProductStatistics> adRows =
                productStatisticsRepository.findByCampaign_Cabinet_IdAndDateBetween(cabinetId, minFrom, maxTo);

        for (PeriodDto period : periods) {
            FunnelTotals funnel = aggregateFunnel(productIds, analyticsByProductId, period);
            AdvertisingTotals advertising = aggregateAdvertising(adRows, skus, period);
            result.put(period.getId(), toAggregatedDto(funnel, advertising));
        }
        return result;
    }

    /**
     * Значение метрики артикула за период.
     */
    public Object calculateArticleValue(
            String metricName,
            OzonProductCard card,
            PeriodDto period,
            List<OzonProductCardAnalytics> analyticsRows,
            List<OzonPromotionCampaignProductStatistics> adRows
    ) {
        FunnelTotals funnel = sumFunnel(analyticsRows, period);
        if (MetricNames.isFunnelMetric(metricName)) {
            return funnelValue(funnel, metricName);
        }
        if (!MetricNames.isAdvertisingMetric(metricName)) {
            return null;
        }
        Long sku = card.getSku();
        if (sku == null) {
            return null;
        }
        AdvertisingTotals advertising = aggregateAdvertising(
                adRows.stream().filter(row -> sku.equals(row.getSku())).toList(),
                Set.of(sku),
                period
        );
        return advertisingValue(advertising, metricName);
    }

    private AggregatedMetricsDto toAggregatedDto(FunnelTotals funnel, AdvertisingTotals advertising) {
        AggregatedMetricsDto dto = new AggregatedMetricsDto();
        dto.setTransitions(funnel.transitions());
        dto.setCart(funnel.cart());
        dto.setOrders(funnel.orders());
        dto.setOrdersAmount(funnel.ordersAmount());
        dto.setCartConversion(funnel.cartConversion());
        dto.setOrderConversion(funnel.orderConversion());
        dto.setViews(advertising.views());
        dto.setClicks(advertising.clicks());
        dto.setCosts(advertising.spend());
        dto.setCpc(advertising.cpc());
        dto.setCtr(advertising.ctr());
        dto.setCpo(advertising.cpo());
        dto.setDrr(advertising.drr());
        return dto;
    }

    private FunnelTotals aggregateFunnel(
            Set<Long> productIds,
            Map<Long, List<OzonProductCardAnalytics>> analyticsByProductId,
            PeriodDto period
    ) {
        int transitions = 0;
        int cart = 0;
        int orders = 0;
        BigDecimal ordersAmount = BigDecimal.ZERO;
        BigDecimal convTocartSum = BigDecimal.ZERO;
        int convTocartCount = 0;

        for (Long productId : productIds) {
            FunnelTotals part = sumFunnel(analyticsByProductId.getOrDefault(productId, List.of()), period);
            transitions += part.transitions();
            cart += part.cart();
            orders += part.orders();
            ordersAmount = ordersAmount.add(part.ordersAmount());
            if (part.avgConvTocart() != null) {
                convTocartSum = convTocartSum.add(part.avgConvTocart());
                convTocartCount++;
            }
        }

        BigDecimal cartConversion = convTocartCount > 0
                ? convTocartSum.divide(BigDecimal.valueOf(convTocartCount), 2, RoundingMode.HALF_UP)
                : MathUtils.calculatePercentage(cart, transitions);
        BigDecimal orderConversion = MathUtils.calculatePercentage(orders, transitions);
        return new FunnelTotals(transitions, cart, orders, ordersAmount, cartConversion, orderConversion);
    }

    private FunnelTotals sumFunnel(List<OzonProductCardAnalytics> rows, PeriodDto period) {
        int transitions = 0;
        int cart = 0;
        int orders = 0;
        BigDecimal ordersAmount = BigDecimal.ZERO;
        BigDecimal convTocartSum = BigDecimal.ZERO;
        int convTocartCount = 0;

        for (OzonProductCardAnalytics row : rows) {
            if (row.getDate().isBefore(period.getDateFrom()) || row.getDate().isAfter(period.getDateTo())) {
                continue;
            }
            transitions += MathUtils.getValueOrZero(row.getHitsViewPdp());
            cart += MathUtils.getValueOrZero(row.getHitsTocart());
            orders += MathUtils.getValueOrZero(row.getOrderedUnits());
            ordersAmount = ordersAmount.add(MathUtils.getValueOrZero(row.getRevenue()));
            if (row.getConvTocart() != null) {
                convTocartSum = convTocartSum.add(row.getConvTocart());
                convTocartCount++;
            }
        }

        BigDecimal avgConv = convTocartCount > 0
                ? convTocartSum.divide(BigDecimal.valueOf(convTocartCount), 4, RoundingMode.HALF_UP)
                : null;
        BigDecimal cartConversion = avgConv != null
                ? avgConv.setScale(2, RoundingMode.HALF_UP)
                : MathUtils.calculatePercentage(cart, transitions);
        BigDecimal orderConversion = MathUtils.calculatePercentage(orders, transitions);
        return new FunnelTotals(transitions, cart, orders, ordersAmount, cartConversion, orderConversion, avgConv);
    }

    private AdvertisingTotals aggregateAdvertising(
            List<OzonPromotionCampaignProductStatistics> rows,
            Set<Long> allowedSkus,
            PeriodDto period
    ) {
        int views = 0;
        int clicks = 0;
        BigDecimal spend = BigDecimal.ZERO;
        int orders = 0;
        BigDecimal ordersMoney = BigDecimal.ZERO;

        for (OzonPromotionCampaignProductStatistics row : rows) {
            if (!allowedSkus.isEmpty() && (row.getSku() == null || !allowedSkus.contains(row.getSku()))) {
                continue;
            }
            if (row.getDate().isBefore(period.getDateFrom()) || row.getDate().isAfter(period.getDateTo())) {
                continue;
            }
            views += MathUtils.getValueOrZero(row.getViews());
            clicks += MathUtils.getValueOrZero(row.getClicks());
            spend = spend.add(MathUtils.getValueOrZero(row.getSpend()));
            orders += MathUtils.getValueOrZero(row.getOrders());
            ordersMoney = ordersMoney.add(MathUtils.getValueOrZero(row.getOrdersMoney()));
        }
        return AdvertisingTotals.of(views, clicks, spend, orders, ordersMoney);
    }

    private static Set<Long> collectSkus(
            Map<Long, List<OzonProductCardAnalytics>> analyticsByProductId,
            Set<Long> productIds
    ) {
        Set<Long> skus = new HashSet<>();
        for (Long productId : productIds) {
            for (OzonProductCardAnalytics row : analyticsByProductId.getOrDefault(productId, List.of())) {
                if (row.getSku() != null) {
                    skus.add(row.getSku());
                }
            }
        }
        return skus;
    }

    private static Object funnelValue(FunnelTotals totals, String metricName) {
        return switch (metricName) {
            case MetricNames.TRANSITIONS -> totals.transitions();
            case MetricNames.CART -> totals.cart();
            case MetricNames.ORDERS -> totals.orders();
            case MetricNames.ORDERS_AMOUNT -> totals.ordersAmount();
            case MetricNames.CART_CONVERSION -> totals.cartConversion();
            case MetricNames.ORDER_CONVERSION -> totals.orderConversion();
            default -> null;
        };
    }

    private static Object advertisingValue(AdvertisingTotals totals, String metricName) {
        return switch (metricName) {
            case MetricNames.VIEWS -> totals.views();
            case MetricNames.CLICKS -> totals.clicks();
            case MetricNames.COSTS -> totals.spend();
            case MetricNames.CPC -> totals.cpc();
            case MetricNames.CTR -> totals.ctr();
            case MetricNames.CPO -> totals.cpo();
            case MetricNames.DRR -> totals.drr();
            default -> null;
        };
    }

    private record FunnelTotals(
            int transitions,
            int cart,
            int orders,
            BigDecimal ordersAmount,
            BigDecimal cartConversion,
            BigDecimal orderConversion,
            BigDecimal avgConvTocart
    ) {
        FunnelTotals(int transitions, int cart, int orders, BigDecimal ordersAmount,
                     BigDecimal cartConversion, BigDecimal orderConversion) {
            this(transitions, cart, orders, ordersAmount, cartConversion, orderConversion, null);
        }
    }

    private record AdvertisingTotals(
            int views,
            int clicks,
            BigDecimal spend,
            int orders,
            BigDecimal ordersMoney,
            BigDecimal cpc,
            BigDecimal ctr,
            BigDecimal cpo,
            BigDecimal drr
    ) {
        static AdvertisingTotals of(int views, int clicks, BigDecimal spend, int orders, BigDecimal ordersMoney) {
            BigDecimal cpc = clicks > 0
                    ? spend.divide(BigDecimal.valueOf(clicks), 2, RoundingMode.HALF_UP)
                    : null;
            BigDecimal ctr = views > 0
                    ? BigDecimal.valueOf(clicks)
                    .multiply(BigDecimal.valueOf(100))
                    .divide(BigDecimal.valueOf(views), 2, RoundingMode.HALF_UP)
                    : null;
            BigDecimal cpo = orders > 0
                    ? spend.divide(BigDecimal.valueOf(orders), 2, RoundingMode.HALF_UP)
                    : null;
            BigDecimal drr = ordersMoney.compareTo(BigDecimal.ZERO) > 0
                    ? spend.multiply(BigDecimal.valueOf(100))
                    .divide(ordersMoney, 2, RoundingMode.HALF_UP)
                    : null;
            return new AdvertisingTotals(views, clicks, spend, orders, ordersMoney, cpc, ctr, cpo, drr);
        }
    }
}
