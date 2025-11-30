package ru.oparin.solution.service.analytics;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.oparin.solution.dto.analytics.AggregatedMetricsDto;
import ru.oparin.solution.dto.analytics.PeriodDto;
import ru.oparin.solution.model.PromotionCampaign;
import ru.oparin.solution.repository.PromotionCampaignRepository;

import java.math.BigDecimal;
import java.util.List;

/**
 * Калькулятор метрик рекламы.
 */
@Component
@RequiredArgsConstructor
public class AdvertisingMetricsCalculator {

    private final PromotionCampaignRepository campaignRepository;
    private final CampaignStatisticsAggregator statisticsAggregator;

    /**
     * Рассчитывает метрики рекламы для периода.
     */
    public void calculateAdvertisingMetrics(
            AggregatedMetricsDto metrics,
            Long sellerId,
            PeriodDto period
    ) {
        List<Long> campaignIds = getCampaignIds(sellerId);
        CampaignStatisticsAggregator.AdvertisingStats stats = statisticsAggregator.aggregateStats(campaignIds, period);

        setBasicMetrics(metrics, stats);
        calculateDerivedMetrics(metrics, stats);
    }

    private List<Long> getCampaignIds(Long sellerId) {
        return campaignRepository.findBySellerId(sellerId).stream()
                .map(PromotionCampaign::getAdvertId)
                .toList();
    }

    private void setBasicMetrics(
            AggregatedMetricsDto metrics,
            CampaignStatisticsAggregator.AdvertisingStats stats
    ) {
        metrics.setViews(stats.views());
        metrics.setClicks(stats.clicks());
        metrics.setCosts(MathUtils.convertKopecksToRubles(stats.sumKopecks()));
    }

    private void calculateDerivedMetrics(
            AggregatedMetricsDto metrics,
            CampaignStatisticsAggregator.AdvertisingStats stats
    ) {
        if (stats.clicks() > 0) {
            BigDecimal cpc = MathUtils.divideKopecksByValue(stats.sumKopecks(), stats.clicks());
            metrics.setCpc(cpc);
        }

        if (stats.views() > 0) {
            BigDecimal ctr = MathUtils.calculatePercentage(stats.clicks(), stats.views());
            metrics.setCtr(ctr);
        }

        if (stats.orders() > 0) {
            BigDecimal cpo = MathUtils.divideKopecksByValue(stats.sumKopecks(), stats.orders());
            metrics.setCpo(cpo);
        }

        if (stats.ordersSumKopecks() > 0) {
            BigDecimal drr = MathUtils.calculatePercentage(stats.sumKopecks(), stats.ordersSumKopecks());
            metrics.setDrr(drr);
        }
    }
}
