package ru.oparin.solution.service.analytics;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.oparin.solution.dto.analytics.PeriodDto;
import ru.oparin.solution.model.PromotionCampaignStatistics;
import ru.oparin.solution.repository.PromotionCampaignStatisticsRepository;

import java.util.List;

/**
 * Агрегатор статистики рекламных кампаний.
 */
@Component
@RequiredArgsConstructor
public class CampaignStatisticsAggregator {

    private final PromotionCampaignStatisticsRepository campaignStatisticsRepository;

    /**
     * Агрегирует статистику кампаний за период.
     */
    public AdvertisingStats aggregateStats(List<Long> campaignIds, PeriodDto period) {
        int views = 0;
        int clicks = 0;
        long sumKopecks = 0;
        int orders = 0;
        long ordersSumKopecks = 0;

        for (Long campaignId : campaignIds) {
            List<PromotionCampaignStatistics> stats = getCampaignStatistics(campaignId, period);
            AdvertisingStats campaignStats = aggregateCampaignStatistics(stats);
            
            views += campaignStats.views();
            clicks += campaignStats.clicks();
            sumKopecks += campaignStats.sumKopecks();
            orders += campaignStats.orders();
            ordersSumKopecks += campaignStats.ordersSumKopecks();
        }

        return new AdvertisingStats(views, clicks, sumKopecks, orders, ordersSumKopecks);
    }

    private List<PromotionCampaignStatistics> getCampaignStatistics(Long campaignId, PeriodDto period) {
        return campaignStatisticsRepository.findByCampaignAdvertIdAndDateBetween(
                campaignId,
                period.getDateFrom(),
                period.getDateTo()
        );
    }

    private AdvertisingStats aggregateCampaignStatistics(List<PromotionCampaignStatistics> stats) {
        int views = 0;
        int clicks = 0;
        long sumKopecks = 0;
        int orders = 0;
        long ordersSumKopecks = 0;

        for (PromotionCampaignStatistics stat : stats) {
            views += MathUtils.getValueOrZero(stat.getViews());
            clicks += MathUtils.getValueOrZero(stat.getClicks());
            sumKopecks += MathUtils.getValueOrZero(stat.getSum());
            orders += MathUtils.getValueOrZero(stat.getOrders());
            ordersSumKopecks += MathUtils.getValueOrZero(stat.getOrdersSum());
        }

        return new AdvertisingStats(views, clicks, sumKopecks, orders, ordersSumKopecks);
    }

    /**
     * Статистика рекламы.
     */
    public record AdvertisingStats(
            int views,
            int clicks,
            long sumKopecks,
            int orders,
            long ordersSumKopecks
    ) {
    }
}

