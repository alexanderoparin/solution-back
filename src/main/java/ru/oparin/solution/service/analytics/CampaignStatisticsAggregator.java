package ru.oparin.solution.service.analytics;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import ru.oparin.solution.dto.analytics.PeriodDto;
import ru.oparin.solution.model.PromotionCampaignStatistics;
import ru.oparin.solution.repository.PromotionCampaignStatisticsRepository;

import java.math.BigDecimal;
import java.util.List;

/**
 * Агрегатор статистики рекламных кампаний.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CampaignStatisticsAggregator {

    private final PromotionCampaignStatisticsRepository campaignStatisticsRepository;

    /**
     * Агрегирует статистику кампаний за период.
     */
    public AdvertisingStats aggregateStats(List<Long> campaignIds, PeriodDto period) {
        // Оптимизированный запрос: получаем статистику для всех кампаний одним запросом
        List<PromotionCampaignStatistics> allStats = campaignStatisticsRepository.findByCampaignAdvertIdInAndDateBetween(
                campaignIds,
                period.getDateFrom(),
                period.getDateTo()
        );
        
        return aggregateCampaignStatistics(allStats);
    }

    /**
     * Агрегирует статистику одной кампании за период.
     */
    public AdvertisingStats aggregateStatsForCampaign(Long campaignId, PeriodDto period) {
        List<PromotionCampaignStatistics> stats = campaignStatisticsRepository.findByCampaignAdvertIdInAndDateBetween(
                List.of(campaignId),
                period.getDateFrom(),
                period.getDateTo()
        );
        
        return aggregateCampaignStatistics(stats);
    }

    private AdvertisingStats aggregateCampaignStatistics(List<PromotionCampaignStatistics> stats) {
        int views = 0;
        int clicks = 0;
        BigDecimal sum = BigDecimal.ZERO;
        int orders = 0;
        BigDecimal ordersSum = BigDecimal.ZERO;

        for (PromotionCampaignStatistics stat : stats) {
            views += MathUtils.getValueOrZero(stat.getViews());
            clicks += MathUtils.getValueOrZero(stat.getClicks());
            if (stat.getSum() != null) {
                sum = sum.add(stat.getSum());
            }
            orders += MathUtils.getValueOrZero(stat.getOrders());
            if (stat.getOrdersSum() != null) {
                ordersSum = ordersSum.add(stat.getOrdersSum());
            }
        }

        return new AdvertisingStats(views, clicks, sum, orders, ordersSum);
    }

    /**
     * Статистика рекламы.
     */
    public record AdvertisingStats(
            int views,
            int clicks,
            BigDecimal sum,
            int orders,
            BigDecimal ordersSum
    ) {
    }
}

