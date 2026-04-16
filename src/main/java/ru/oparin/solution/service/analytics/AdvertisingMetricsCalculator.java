package ru.oparin.solution.service.analytics;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.oparin.solution.dto.analytics.AggregatedMetricsDto;
import ru.oparin.solution.dto.analytics.PeriodDto;
import ru.oparin.solution.model.PromotionCampaign;
import ru.oparin.solution.repository.PromotionCampaignRepository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
     * Рассчитывает метрики рекламы для периода (при cabinetId != null — только кампании кабинета).
     */
    public void calculateAdvertisingMetrics(
            AggregatedMetricsDto metrics,
            Long sellerId,
            Long cabinetId,
            PeriodDto period
    ) {
        calculateAdvertisingMetrics(metrics, sellerId, cabinetId, period, null);
    }

    /**
     * Рассчитывает метрики рекламы для периода с учётом фильтра по артикулам.
     * Если nmIdsFilter не пустой — суммируются только данные по указанным артикулам (для сводной с фильтром).
     */
    public void calculateAdvertisingMetrics(
            AggregatedMetricsDto metrics,
            Long sellerId,
            Long cabinetId,
            PeriodDto period,
            Set<Long> nmIdsFilter
    ) {
        List<Long> campaignIds = getCampaignIds(sellerId, cabinetId);
        CampaignStatisticsAggregator.AdvertisingStats stats;
        if (nmIdsFilter != null && !nmIdsFilter.isEmpty()) {
            Map<Long, CampaignStatisticsAggregator.AdvertisingStats> byArticle =
                    statisticsAggregator.aggregateStatsByArticle(campaignIds, period);
            int views = 0;
            int clicks = 0;
            BigDecimal sum = BigDecimal.ZERO;
            int orders = 0;
            BigDecimal ordersSum = BigDecimal.ZERO;
            for (Long nmId : nmIdsFilter) {
                CampaignStatisticsAggregator.AdvertisingStats s = byArticle.get(nmId);
                if (s != null) {
                    views += s.views();
                    clicks += s.clicks();
                    sum = sum.add(s.sum());
                    orders += s.orders();
                    ordersSum = ordersSum.add(s.ordersSum());
                }
            }
            stats = new CampaignStatisticsAggregator.AdvertisingStats(views, clicks, sum, orders, ordersSum);
        } else {
            stats = statisticsAggregator.aggregateStats(campaignIds, period);
        }
        setBasicMetrics(metrics, stats);
        calculateDerivedMetrics(metrics, stats);
        // СРО и ДРР по тем же «Заказали»/«Заказали на сумму», что в сводке (воронка), если они уже заданы
        alignCpoAndDrrWithFunnel(metrics);
    }

    /**
     * Если в metrics уже есть orders/ordersAmount (из воронки), пересчитываем СРО и ДРР по ним,
     * чтобы в сводке формулы совпадали с отображаемыми «Заказали, шт» и «Заказали на сумму».
     */
    private void alignCpoAndDrrWithFunnel(AggregatedMetricsDto metrics) {
        BigDecimal costs = metrics.getCosts();
        if (costs == null) return;
        Integer orders = metrics.getOrders();
        if (orders != null && orders > 0) {
            metrics.setCpo(MathUtils.divideSafely(costs, BigDecimal.valueOf(orders)));
        }
        BigDecimal ordersAmount = metrics.getOrdersAmount();
        if (ordersAmount != null && ordersAmount.compareTo(BigDecimal.ZERO) > 0) {
            metrics.setDrr(MathUtils.calculatePercentage(costs, ordersAmount));
        }
    }

    private List<Long> getCampaignIds(Long sellerId, Long cabinetId) {
        List<PromotionCampaign> campaigns = cabinetId != null
                ? campaignRepository.findByCabinet_Id(cabinetId)
                : campaignRepository.findByCabinet_User_Id(sellerId);
        return campaigns.stream()
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
