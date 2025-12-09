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
 * Рассчитывает базовые и производные метрики рекламных кампаний для указанного периода.
 */
@Component
@RequiredArgsConstructor
public class AdvertisingMetricsCalculator {

    private final PromotionCampaignRepository campaignRepository;
    private final CampaignStatisticsAggregator statisticsAggregator;

    /**
     * Рассчитывает метрики рекламы для периода и заполняет переданный DTO.
     *
     * @param metrics DTO для заполнения метриками
     * @param sellerId ID продавца
     * @param period период для расчета метрик
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

    /**
     * Получает список ID рекламных кампаний продавца.
     *
     * @param sellerId ID продавца
     * @return список ID кампаний (advert_id)
     */
    private List<Long> getCampaignIds(Long sellerId) {
        return campaignRepository.findBySellerId(sellerId).stream()
                .map(PromotionCampaign::getAdvertId)
                .toList();
    }

    /**
     * Устанавливает базовые метрики (просмотры, клики, затраты).
     *
     * @param metrics DTO для заполнения
     * @param stats агрегированная статистика рекламы
     */
    private void setBasicMetrics(
            AggregatedMetricsDto metrics,
            CampaignStatisticsAggregator.AdvertisingStats stats
    ) {
        metrics.setViews(stats.views());
        metrics.setClicks(stats.clicks());
        metrics.setCosts(stats.sum());
    }

    /**
     * Рассчитывает производные метрики (CPC, CTR, CPO, DRR).
     *
     * @param metrics DTO для заполнения
     * @param stats агрегированная статистика рекламы
     */
    private void calculateDerivedMetrics(
            AggregatedMetricsDto metrics,
            CampaignStatisticsAggregator.AdvertisingStats stats
    ) {
        calculateCpc(metrics, stats);
        calculateCtr(metrics, stats);
        calculateCpo(metrics, stats);
        calculateDrr(metrics, stats);
    }

    /**
     * Рассчитывает CPC (Cost Per Click) - стоимость клика.
     * Формула: затраты / количество кликов.
     *
     * @param metrics DTO для заполнения
     * @param stats агрегированная статистика рекламы
     */
    private void calculateCpc(
            AggregatedMetricsDto metrics,
            CampaignStatisticsAggregator.AdvertisingStats stats
    ) {
        if (stats.clicks() > 0) {
            BigDecimal cpc = MathUtils.divideSafely(stats.sum(), stats.clicks());
            metrics.setCpc(cpc);
        }
    }

    /**
     * Рассчитывает CTR (Click-Through Rate) - процент кликов от показов.
     * Формула: (клики / просмотры) * 100.
     *
     * @param metrics DTO для заполнения
     * @param stats агрегированная статистика рекламы
     */
    private void calculateCtr(
            AggregatedMetricsDto metrics,
            CampaignStatisticsAggregator.AdvertisingStats stats
    ) {
        if (stats.views() > 0) {
            BigDecimal ctr = MathUtils.calculatePercentage(stats.clicks(), stats.views());
            metrics.setCtr(ctr);
        }
    }

    /**
     * Рассчитывает CPO (Cost Per Order) - стоимость заказа.
     * Формула: затраты / количество заказов.
     *
     * @param metrics DTO для заполнения
     * @param stats агрегированная статистика рекламы
     */
    private void calculateCpo(
            AggregatedMetricsDto metrics,
            CampaignStatisticsAggregator.AdvertisingStats stats
    ) {
        if (stats.orders() > 0) {
            BigDecimal cpo = MathUtils.divideSafely(stats.sum(), stats.orders());
            metrics.setCpo(cpo);
        }
    }

    /**
     * Рассчитывает DRR (Доля расходов на рекламу) - процент затрат от суммы заказов.
     * Формула: (затраты / сумма заказов) * 100.
     *
     * @param metrics DTO для заполнения
     * @param stats агрегированная статистика рекламы
     */
    private void calculateDrr(
            AggregatedMetricsDto metrics,
            CampaignStatisticsAggregator.AdvertisingStats stats
    ) {
        if (MathUtils.isPositive(stats.ordersSum()) && MathUtils.isPositive(stats.sum())) {
            BigDecimal drr = MathUtils.calculatePercentage(stats.sum(), stats.ordersSum());
            metrics.setDrr(drr);
        }
    }
}
