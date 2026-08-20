package ru.oparin.solution.service.analytics;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import ru.oparin.solution.dto.analytics.PeriodDto;
import ru.oparin.solution.model.WbPromotionCampaignStatistics;
import ru.oparin.solution.repository.WbPromotionCampaignStatisticsRepository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Агрегатор статистики рекламных кампаний.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WbCampaignStatisticsAggregator {

    private final WbPromotionCampaignStatisticsRepository campaignStatisticsRepository;

    /**
     * Агрегирует статистику кампаний за период.
     */
    public AdvertisingStats aggregateStats(List<Long> campaignIds, PeriodDto period) {
        // Оптимизированный запрос: получаем статистику для всех кампаний одним запросом
        List<WbPromotionCampaignStatistics> allStats = campaignStatisticsRepository.findByCampaignAdvertIdInAndDateBetween(
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
        List<WbPromotionCampaignStatistics> stats = campaignStatisticsRepository.findByCampaignAdvertIdInAndDateBetween(
                List.of(campaignId),
                period.getDateFrom(),
                period.getDateTo()
        );
        
        return aggregateCampaignStatistics(stats);
    }

    /**
     * Агрегирует статистику по артикулам (nmId) за период: по каждому артикулу суммируются данные
     * по всем кампаниям из campaignIds.
     */
    public Map<Long, AdvertisingStats> aggregateStatsByArticle(List<Long> campaignIds, PeriodDto period) {
        List<WbPromotionCampaignStatistics> allStats = campaignStatisticsRepository.findByCampaignAdvertIdInAndDateBetween(
                campaignIds,
                period.getDateFrom(),
                period.getDateTo()
        );
        return allStats.stream()
                .collect(Collectors.groupingBy(WbPromotionCampaignStatistics::getNmId))
                .entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> aggregateCampaignStatistics(e.getValue())));
    }

    private AdvertisingStats aggregateCampaignStatistics(List<WbPromotionCampaignStatistics> stats) {
        int views = 0;
        int clicks = 0;
        BigDecimal sum = BigDecimal.ZERO;
        int orders = 0;
        BigDecimal ordersSum = BigDecimal.ZERO;

        for (WbPromotionCampaignStatistics stat : stats) {
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

