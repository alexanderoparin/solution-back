package ru.oparin.solution.service.analytics;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import ru.oparin.solution.dto.analytics.PeriodDto;
import ru.oparin.solution.model.ProductCard;
import ru.oparin.solution.model.ProductCardAnalytics;
import ru.oparin.solution.model.PromotionCampaign;
import ru.oparin.solution.repository.ProductCardAnalyticsRepository;
import ru.oparin.solution.repository.PromotionCampaignRepository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

import static ru.oparin.solution.service.analytics.MetricNames.*;

/**
 * Калькулятор значений метрик для артикула.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MetricValueCalculator {

    private final ProductCardAnalyticsRepository analyticsRepository;
    private final PromotionCampaignRepository campaignRepository;
    private final CampaignStatisticsAggregator statisticsAggregator;

    /**
     * Рассчитывает значение метрики для артикула за период.
     */
    public Object calculateValue(
            ProductCard card,
            String metricName,
            PeriodDto period,
            Long sellerId,
            Map<PeriodDto, CampaignStatisticsAggregator.AdvertisingStats> advertisingStatsCache
    ) {
        return switch (metricName) {
            case TRANSITIONS -> sumField(card.getNmId(), period, ProductCardAnalytics::getOpenCard);
            case CART -> sumField(card.getNmId(), period, ProductCardAnalytics::getAddToCart);
            case ORDERS -> sumField(card.getNmId(), period, ProductCardAnalytics::getOrders);
            case ORDERS_AMOUNT -> sumAmount(card.getNmId(), period);
            case CART_CONVERSION -> calculateCartConversion(card.getNmId(), period);
            case ORDER_CONVERSION -> calculateOrderConversion(card.getNmId(), period);
            case VIEWS, CLICKS, COSTS, CPC,
                 CTR, CPO, DRR ->
                    calculateAdvertisingMetric(metricName, period, sellerId, card.getNmId(), advertisingStatsCache);
            default -> null;
        };
    }

    private Integer sumField(
            Long nmId,
            PeriodDto period,
            Function<ProductCardAnalytics, Integer> extractor
    ) {
        List<ProductCardAnalytics> analytics = getAnalytics(nmId, period);
        return sumField(analytics, extractor);
    }

    private Integer sumField(
            List<ProductCardAnalytics> analytics,
            Function<ProductCardAnalytics, Integer> extractor
    ) {
        return analytics.stream()
                .map(extractor)
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue)
                .sum();
    }

    private BigDecimal sumAmount(Long nmId, PeriodDto period) {
        List<ProductCardAnalytics> analytics = getAnalytics(nmId, period);
        return analytics.stream()
                .map(ProductCardAnalytics::getOrdersSum)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal calculateCartConversion(Long nmId, PeriodDto period) {
        List<ProductCardAnalytics> analytics = getAnalytics(nmId, period);
        int transitions = sumField(analytics, ProductCardAnalytics::getOpenCard);
        int cart = sumField(analytics, ProductCardAnalytics::getAddToCart);

        return MathUtils.calculatePercentage(cart, transitions);
    }

    private BigDecimal calculateOrderConversion(Long nmId, PeriodDto period) {
        List<ProductCardAnalytics> analytics = getAnalytics(nmId, period);
        int cart = sumField(analytics, ProductCardAnalytics::getAddToCart);
        int orders = sumField(analytics, ProductCardAnalytics::getOrders);

        return MathUtils.calculatePercentage(orders, cart);
    }

    private Object calculateAdvertisingMetric(
            String metricName, 
            PeriodDto period, 
            Long sellerId, 
            Long nmId,
            Map<PeriodDto, CampaignStatisticsAggregator.AdvertisingStats> advertisingStatsCache
    ) {
        // Используем кэшированную статистику, если она есть, иначе получаем заново
        CampaignStatisticsAggregator.AdvertisingStats stats;
        if (advertisingStatsCache != null && advertisingStatsCache.containsKey(period)) {
            stats = advertisingStatsCache.get(period);
        } else {
            // Fallback: если кэш не передан, получаем статистику заново
            List<Long> campaignIds = getCampaignIds(sellerId);
            stats = statisticsAggregator.aggregateStats(campaignIds, period);
        }

        return switch (metricName) {
            case VIEWS -> stats.views();
            case CLICKS -> stats.clicks();
            case COSTS -> MathUtils.convertKopecksToRubles(stats.sumKopecks());
            case CPC -> calculateCpc(stats);
            case CTR -> calculateCtr(stats);
            case CPO -> calculateCpo(stats);
            case DRR -> calculateDrr(stats);
            default -> null;
        };
    }

    private BigDecimal calculateCpc(CampaignStatisticsAggregator.AdvertisingStats stats) {
        return MathUtils.divideKopecksByValue(stats.sumKopecks(), stats.clicks());
    }

    private BigDecimal calculateCtr(CampaignStatisticsAggregator.AdvertisingStats stats) {
        return MathUtils.calculatePercentage(stats.clicks(), stats.views());
    }

    private BigDecimal calculateCpo(CampaignStatisticsAggregator.AdvertisingStats stats) {
        return MathUtils.divideKopecksByValue(stats.sumKopecks(), stats.orders());
    }

    private BigDecimal calculateDrr(CampaignStatisticsAggregator.AdvertisingStats stats) {
        return MathUtils.calculatePercentage(stats.sumKopecks(), stats.ordersSumKopecks());
    }

    private List<ProductCardAnalytics> getAnalytics(Long nmId, PeriodDto period) {
        return analyticsRepository.findByProductCardNmIdAndDateBetween(
                nmId,
                period.getDateFrom(),
                period.getDateTo()
        );
    }

    private List<Long> getCampaignIds(Long sellerId) {
        return campaignRepository.findBySellerId(sellerId).stream()
                .map(PromotionCampaign::getAdvertId)
                .toList();
    }
}
