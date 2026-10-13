package ru.oparin.solution.service.analytics;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import ru.oparin.solution.dto.analytics.PeriodDto;
import ru.oparin.solution.model.WbPromotionCampaignStatistics;
import ru.oparin.solution.repository.WbPromotionCampaignStatisticsRepository;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Collections;
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
        if (campaignIds == null || campaignIds.isEmpty()) {
            return emptyStats();
        }
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
     * Статистика рекламы одного артикула за период (по кабинету или продавцу).
     * Без загрузки всех кампаний кабинета.
     */
    public AdvertisingStats aggregateStatsForArticle(Long cabinetId, Long sellerId, Long nmId, PeriodDto period) {
        if (nmId == null || period == null) {
            return emptyStats();
        }
        List<WbPromotionCampaignStatistics> stats;
        if (cabinetId != null) {
            stats = campaignStatisticsRepository.findByCabinetIdAndNmIdAndDateBetween(
                    cabinetId, nmId, period.getDateFrom(), period.getDateTo());
        } else if (sellerId != null) {
            stats = campaignStatisticsRepository.findBySellerIdAndNmIdAndDateBetween(
                    sellerId, nmId, period.getDateFrom(), period.getDateTo());
        } else {
            return emptyStats();
        }
        return aggregateCampaignStatistics(stats);
    }

    /**
     * Агрегирует статистику по артикулам кабинета за период.
     */
    public Map<Long, AdvertisingStats> aggregateStatsByArticleForCabinet(Long cabinetId, PeriodDto period) {
        if (cabinetId == null || period == null) {
            return Collections.emptyMap();
        }
        List<WbPromotionCampaignStatistics> allStats = campaignStatisticsRepository.findByCabinetIdAndDateBetween(
                cabinetId, period.getDateFrom(), period.getDateTo());
        return groupByArticle(allStats);
    }

    /**
     * Агрегирует статистику по указанным артикулам кабинета за период.
     */
    public Map<Long, AdvertisingStats> aggregateStatsByArticles(
            Long cabinetId,
            Collection<Long> nmIds,
            PeriodDto period
    ) {
        if (cabinetId == null || nmIds == null || nmIds.isEmpty() || period == null) {
            return Collections.emptyMap();
        }
        List<WbPromotionCampaignStatistics> allStats =
                campaignStatisticsRepository.findByCabinetIdAndNmIdInAndDateBetween(
                        cabinetId, nmIds, period.getDateFrom(), period.getDateTo());
        return groupByArticle(allStats);
    }

    /**
     * Агрегирует статистику по артикулам за период: по каждому артикулу суммируются данные
     * по всем кампаниям из campaignIds.
     *
     * @deprecated предпочтительнее {@link #aggregateStatsByArticleForCabinet} / {@link #aggregateStatsByArticles}
     */
    @Deprecated
    public Map<Long, AdvertisingStats> aggregateStatsByArticle(List<Long> campaignIds, PeriodDto period) {
        if (campaignIds == null || campaignIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<WbPromotionCampaignStatistics> allStats = campaignStatisticsRepository.findByCampaignAdvertIdInAndDateBetween(
                campaignIds,
                period.getDateFrom(),
                period.getDateTo()
        );
        return groupByArticle(allStats);
    }

    private Map<Long, AdvertisingStats> groupByArticle(List<WbPromotionCampaignStatistics> allStats) {
        return allStats.stream()
                .filter(s -> s.getNmId() != null)
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

    private static AdvertisingStats emptyStats() {
        return new AdvertisingStats(0, 0, BigDecimal.ZERO, 0, BigDecimal.ZERO);
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
