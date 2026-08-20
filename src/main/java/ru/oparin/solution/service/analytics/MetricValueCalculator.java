package ru.oparin.solution.service.analytics;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import ru.oparin.solution.dto.analytics.PeriodDto;
import ru.oparin.solution.model.WbProductCard;
import ru.oparin.solution.model.WbProductCardAnalytics;
import ru.oparin.solution.model.WbPromotionCampaign;
import ru.oparin.solution.repository.WbProductCardAnalyticsRepository;
import ru.oparin.solution.repository.WbPromotionCampaignRepository;

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

    private final WbProductCardAnalyticsRepository analyticsRepository;
    private final WbPromotionCampaignRepository campaignRepository;
    private final WbCampaignStatisticsAggregator statisticsAggregator;

    /**
     * Рассчитывает значение метрики для артикула за период.
     */
    public Object calculateValue(
            WbProductCard card,
            String metricName,
            PeriodDto period,
            Long sellerId,
            Long cabinetId,
            Map<PeriodDto, WbCampaignStatisticsAggregator.AdvertisingStats> advertisingStatsCache
    ) {
        Long cardCabinetId = card.getCabinet() != null ? card.getCabinet().getId() : cabinetId;
        return switch (metricName) {
            case TRANSITIONS -> sumField(card.getNmId(), cardCabinetId, period, WbProductCardAnalytics::getOpenCard);
            case CART -> sumField(card.getNmId(), cardCabinetId, period, WbProductCardAnalytics::getAddToCart);
            case ORDERS -> sumField(card.getNmId(), cardCabinetId, period, WbProductCardAnalytics::getOrders);
            case ORDERS_AMOUNT -> sumAmount(card.getNmId(), cardCabinetId, period);
            case CART_CONVERSION -> calculateCartConversion(card.getNmId(), cardCabinetId, period);
            case ORDER_CONVERSION -> calculateOrderConversion(card.getNmId(), cardCabinetId, period);
            case VIEWS, CLICKS, COSTS, CPC,
                 CTR, CPO, DRR ->
                    calculateAdvertisingMetric(metricName, period, sellerId, cardCabinetId, card.getNmId(), advertisingStatsCache);
            default -> null;
        };
    }

    private Integer sumField(
            Long nmId,
            Long cabinetId,
            PeriodDto period,
            Function<WbProductCardAnalytics, Integer> extractor
    ) {
        List<WbProductCardAnalytics> analytics = getAnalytics(nmId, cabinetId, period);
        return sumField(analytics, extractor);
    }

    private Integer sumField(
            List<WbProductCardAnalytics> analytics,
            Function<WbProductCardAnalytics, Integer> extractor
    ) {
        return analytics.stream()
                .map(extractor)
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue)
                .sum();
    }

    private BigDecimal sumAmount(Long nmId, Long cabinetId, PeriodDto period) {
        List<WbProductCardAnalytics> analytics = getAnalytics(nmId, cabinetId, period);
        return analytics.stream()
                .map(WbProductCardAnalytics::getOrdersSum)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal calculateCartConversion(Long nmId, Long cabinetId, PeriodDto period) {
        List<WbProductCardAnalytics> analytics = getAnalytics(nmId, cabinetId, period);
        int transitions = sumField(analytics, WbProductCardAnalytics::getOpenCard);
        int cart = sumField(analytics, WbProductCardAnalytics::getAddToCart);

        return MathUtils.calculatePercentage(cart, transitions);
    }

    private BigDecimal calculateOrderConversion(Long nmId, Long cabinetId, PeriodDto period) {
        List<WbProductCardAnalytics> analytics = getAnalytics(nmId, cabinetId, period);
        int cart = sumField(analytics, WbProductCardAnalytics::getAddToCart);
        int orders = sumField(analytics, WbProductCardAnalytics::getOrders);

        return MathUtils.calculatePercentage(orders, cart);
    }

    private Object calculateAdvertisingMetric(
            String metricName,
            PeriodDto period,
            Long sellerId,
            Long cabinetId,
            Long nmId,
            Map<PeriodDto, WbCampaignStatisticsAggregator.AdvertisingStats> advertisingStatsCache
    ) {
        WbCampaignStatisticsAggregator.AdvertisingStats stats;
        if (advertisingStatsCache != null && advertisingStatsCache.containsKey(period)) {
            stats = advertisingStatsCache.get(period);
        } else {
            List<Long> campaignIds = getCampaignIds(sellerId, cabinetId);
            stats = statisticsAggregator.aggregateStats(campaignIds, period);
        }

        // СРО и ДРР по тем же «Заказали»/«Заказали на сумму», что в таблице (воронка по артикулу)
        if (nmId != null && (CPO.equals(metricName) || DRR.equals(metricName))) {
            WbCampaignStatisticsAggregator.AdvertisingStats articleStats = getAdvertisingStatsForArticle(sellerId, cabinetId, period, nmId);
            if (articleStats != null && articleStats.sum() != null) {
                int funnelOrders = sumField(nmId, cabinetId, period, WbProductCardAnalytics::getOrders);
                BigDecimal funnelOrdersSum = sumAmount(nmId, cabinetId, period);
                if (CPO.equals(metricName) && funnelOrders > 0) {
                    return MathUtils.divideSafely(articleStats.sum(), BigDecimal.valueOf(funnelOrders));
                }
                if (DRR.equals(metricName) && funnelOrdersSum.compareTo(BigDecimal.ZERO) > 0) {
                    return MathUtils.calculatePercentage(articleStats.sum(), funnelOrdersSum);
                }
            }
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

    private WbCampaignStatisticsAggregator.AdvertisingStats getAdvertisingStatsForArticle(Long sellerId, Long cabinetId, PeriodDto period, Long nmId) {
        List<Long> campaignIds = getCampaignIds(sellerId, cabinetId);
        Map<Long, WbCampaignStatisticsAggregator.AdvertisingStats> byArticle =
                statisticsAggregator.aggregateStatsByArticle(campaignIds, period);
        return byArticle.get(nmId);
    }

    private BigDecimal calculateCpc(WbCampaignStatisticsAggregator.AdvertisingStats stats) {
        if (stats.clicks() == 0) {
            return null;
        }
        return stats.sum().divide(BigDecimal.valueOf(stats.clicks()), 2, java.math.RoundingMode.HALF_UP);
    }

    private BigDecimal calculateCtr(WbCampaignStatisticsAggregator.AdvertisingStats stats) {
        return MathUtils.calculatePercentage(stats.clicks(), stats.views());
    }

    private BigDecimal calculateCpo(WbCampaignStatisticsAggregator.AdvertisingStats stats) {
        if (stats.orders() == 0) {
            return null;
        }
        return stats.sum().divide(BigDecimal.valueOf(stats.orders()), 2, java.math.RoundingMode.HALF_UP);
    }

    private BigDecimal calculateDrr(WbCampaignStatisticsAggregator.AdvertisingStats stats) {
        if (stats.sum().compareTo(BigDecimal.ZERO) == 0 || stats.ordersSum().compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        // ДРР (доля рекламных расходов) = (расходы / сумма заказов) * 100
        return MathUtils.calculatePercentage(stats.sum(), stats.ordersSum());
    }

    private List<WbProductCardAnalytics> getAnalytics(Long nmId, Long cabinetId, PeriodDto period) {
        if (cabinetId != null) {
            return analyticsRepository.findByCabinet_IdAndProductCardNmIdAndDateBetween(
                    cabinetId, nmId, period.getDateFrom(), period.getDateTo());
        }
        return analyticsRepository.findByProductCardNmIdAndDateBetween(
                nmId, period.getDateFrom(), period.getDateTo());
    }

    private List<Long> getCampaignIds(Long sellerId, Long cabinetId) {
        List<WbPromotionCampaign> campaigns = cabinetId != null
                ? campaignRepository.findByCabinet_Id(cabinetId)
                : campaignRepository.findByCabinet_User_Id(sellerId);
        return campaigns.stream()
                .map(WbPromotionCampaign::getAdvertId)
                .toList();
    }
}
