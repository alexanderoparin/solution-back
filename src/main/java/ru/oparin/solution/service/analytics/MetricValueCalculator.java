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
            Long cabinetId,
            Map<PeriodDto, CampaignStatisticsAggregator.AdvertisingStats> advertisingStatsCache
    ) {
        Long cardCabinetId = card.getCabinet() != null ? card.getCabinet().getId() : cabinetId;
        return switch (metricName) {
            case TRANSITIONS -> sumField(card.getNmId(), cardCabinetId, period, ProductCardAnalytics::getOpenCard);
            case CART -> sumField(card.getNmId(), cardCabinetId, period, ProductCardAnalytics::getAddToCart);
            case ORDERS -> sumField(card.getNmId(), cardCabinetId, period, ProductCardAnalytics::getOrders);
            case ORDERS_AMOUNT -> sumAmount(card.getNmId(), cardCabinetId, period);
            case CART_CONVERSION -> calculateCartConversion(card.getNmId(), cardCabinetId, period);
            case ORDER_CONVERSION -> calculateOrderConversion(card.getNmId(), cardCabinetId, period);
            case VIEWS, CLICKS, COSTS, CPC,
                 CTR, CPO, DRR ->
                    calculateAdvertisingMetric(metricName, period, sellerId, cabinetId, card.getNmId(), advertisingStatsCache);
            default -> null;
        };
    }

    private Integer sumField(
            Long nmId,
            Long cabinetId,
            PeriodDto period,
            Function<ProductCardAnalytics, Integer> extractor
    ) {
        List<ProductCardAnalytics> analytics = getAnalytics(nmId, cabinetId, period);
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

    private BigDecimal sumAmount(Long nmId, Long cabinetId, PeriodDto period) {
        List<ProductCardAnalytics> analytics = getAnalytics(nmId, cabinetId, period);
        return analytics.stream()
                .map(ProductCardAnalytics::getOrdersSum)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal calculateCartConversion(Long nmId, Long cabinetId, PeriodDto period) {
        List<ProductCardAnalytics> analytics = getAnalytics(nmId, cabinetId, period);
        int transitions = sumField(analytics, ProductCardAnalytics::getOpenCard);
        int cart = sumField(analytics, ProductCardAnalytics::getAddToCart);

        return MathUtils.calculatePercentage(cart, transitions);
    }

    private BigDecimal calculateOrderConversion(Long nmId, Long cabinetId, PeriodDto period) {
        List<ProductCardAnalytics> analytics = getAnalytics(nmId, cabinetId, period);
        int cart = sumField(analytics, ProductCardAnalytics::getAddToCart);
        int orders = sumField(analytics, ProductCardAnalytics::getOrders);

        return MathUtils.calculatePercentage(orders, cart);
    }

    private Object calculateAdvertisingMetric(
            String metricName,
            PeriodDto period,
            Long sellerId,
            Long cabinetId,
            Long nmId,
            Map<PeriodDto, CampaignStatisticsAggregator.AdvertisingStats> advertisingStatsCache
    ) {
        CampaignStatisticsAggregator.AdvertisingStats stats;
        if (advertisingStatsCache != null && advertisingStatsCache.containsKey(period)) {
            stats = advertisingStatsCache.get(period);
        } else {
            List<Long> campaignIds = getCampaignIds(sellerId, cabinetId);
            stats = statisticsAggregator.aggregateStats(campaignIds, period);
        }

        return switch (metricName) {
            case VIEWS -> stats.views();
            case CLICKS -> stats.clicks();
            case COSTS -> stats.sum();
            case CPC -> calculateCpc(stats);
            case CTR -> calculateCtr(stats);
            case CPO -> calculateCpo(stats);
            case DRR -> calculateDrr(stats);
            default -> null;
        };
    }

    private BigDecimal calculateCpc(CampaignStatisticsAggregator.AdvertisingStats stats) {
        if (stats.clicks() == 0) {
            return null;
        }
        return stats.sum().divide(BigDecimal.valueOf(stats.clicks()), 2, java.math.RoundingMode.HALF_UP);
    }

    private BigDecimal calculateCtr(CampaignStatisticsAggregator.AdvertisingStats stats) {
        return MathUtils.calculatePercentage(stats.clicks(), stats.views());
    }

    private BigDecimal calculateCpo(CampaignStatisticsAggregator.AdvertisingStats stats) {
        if (stats.orders() == 0) {
            return null;
        }
        return stats.sum().divide(BigDecimal.valueOf(stats.orders()), 2, java.math.RoundingMode.HALF_UP);
    }

    private BigDecimal calculateDrr(CampaignStatisticsAggregator.AdvertisingStats stats) {
        if (stats.sum().compareTo(BigDecimal.ZERO) == 0 || stats.ordersSum().compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        // ДРР (доля рекламных расходов) = (расходы / сумма заказов) * 100
        return MathUtils.calculatePercentage(stats.sum(), stats.ordersSum());
    }

    private List<ProductCardAnalytics> getAnalytics(Long nmId, Long cabinetId, PeriodDto period) {
        if (cabinetId != null) {
            return analyticsRepository.findByCabinet_IdAndProductCardNmIdAndDateBetween(
                    cabinetId, nmId, period.getDateFrom(), period.getDateTo());
        }
        return analyticsRepository.findByProductCardNmIdAndDateBetween(
                nmId, period.getDateFrom(), period.getDateTo());
    }

    private List<Long> getCampaignIds(Long sellerId, Long cabinetId) {
        List<PromotionCampaign> campaigns = cabinetId != null
                ? campaignRepository.findByCabinet_Id(cabinetId)
                : campaignRepository.findBySellerId(sellerId);
        return campaigns.stream()
                .map(PromotionCampaign::getAdvertId)
                .toList();
    }
}
