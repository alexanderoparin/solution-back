package ru.oparin.solution.service.analytics.wb;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.oparin.solution.dto.analytics.*;
import ru.oparin.solution.model.User;
import ru.oparin.solution.model.WbProductCard;
import ru.oparin.solution.model.WbProductCardAnalytics;
import ru.oparin.solution.repository.WbProductCardAnalyticsRepository;
import ru.oparin.solution.service.analytics.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Сводная аналитика и группа метрик WB.
 */
@Service
@RequiredArgsConstructor
public class WbAnalyticsSummaryQuery {

    private final WbArticleCatalogQuery articleCatalogQuery;
    private final WbCampaignAnalyticsQuery campaignAnalyticsQuery;
    private final FunnelMetricsCalculator funnelMetricsCalculator;
    private final AdvertisingMetricsCalculator advertisingMetricsCalculator;
    private final MetricValueCalculator metricValueCalculator;
    private final WbCampaignStatisticsAggregator campaignStatisticsAggregator;
    private final WbProductCardAnalyticsRepository analyticsRepository;

    /**
     * Сводная по артикулам WB: страница или полный список с агрегатами.
     */
    @Transactional(readOnly = true)
    public SummaryResponseDto getSummary(User seller, Long cabinetId, AnalyticsSummaryRequest request) {
        List<PeriodDto> sortedPeriods = AnalyticsPercentChange.sortPeriodsByDateFrom(request.getPeriods());
        List<WbProductCard> visibleCards = articleCatalogQuery.applyCatalogFilters(
                articleCatalogQuery.getVisibleCards(seller.getId(), cabinetId, request.getExcludedNmIds()),
                seller.getId(),
                cabinetId,
                request.getOnlyWithPhoto(),
                request.getOnlyPriority(),
                request.getOnlyInAdvertising()
        );

        ArticleSummarySortField resolvedSortBy = ArticleSummarySortField.fromParam(request.getSortBy());
        Sort.Direction resolvedSortDir = Sort.Direction.fromOptionalString(request.getSortDir()).orElse(Sort.Direction.DESC);
        boolean itemRatingSupported = articleCatalogQuery.isItemRatingSupported(seller.getId(), cabinetId);

        if (CatalogPageHelper.isPaginated(request.getPage(), request.getSize())) {
            List<WbProductCard> filtered = CatalogPageHelper.applyInclusionFilter(
                    visibleCards, request.getFilterToNone(), request.getIncludedNmIds(), WbProductCard::getNmId);
            CatalogPageHelper.Slice<WbProductCard> slice = CatalogPageHelper.filterSearchSortAndPaginate(
                    filtered,
                    request.getSearch(),
                    cards -> articleCatalogQuery.filterCardsBySearch(cards, request.getSearch().trim()),
                    cards -> {
                        articleCatalogQuery.sortProductCards(cards, resolvedSortBy, resolvedSortDir);
                        return cards;
                    },
                    request.getPage(),
                    request.getSize()
            );
            return SummaryResponseDto.builder()
                    .periods(sortedPeriods)
                    .articles(articleCatalogQuery.mapToArticleSummaries(slice.items(), itemRatingSupported))
                    .aggregatedMetrics(null)
                    .totalArticles((long) slice.total())
                    .build();
        }

        Map<Integer, AggregatedMetricsDto> aggregatedMetrics = calculateAggregatedMetrics(
                visibleCards, sortedPeriods, seller.getId(), cabinetId);
        List<WbProductCard> sortedCards = new ArrayList<>(visibleCards);
        articleCatalogQuery.sortProductCards(sortedCards, resolvedSortBy, resolvedSortDir);
        return SummaryResponseDto.builder()
                .periods(sortedPeriods)
                .articles(articleCatalogQuery.mapToArticleSummaries(sortedCards, itemRatingSupported))
                .aggregatedMetrics(aggregatedMetrics)
                .totalArticles(null)
                .build();
    }

    /**
     * Детальные значения одной метрики по всем видимым артикулам.
     */
    @Transactional(readOnly = true)
    public MetricGroupResponseDto getMetricGroup(
            User seller,
            Long cabinetId,
            String metricName,
            AnalyticsSummaryRequest request
    ) {
        List<PeriodDto> sortedPeriods = AnalyticsPercentChange.sortPeriodsByDateFrom(request.getPeriods());
        if (MetricNames.isAdvertisingMetric(metricName)) {
            return getAdvertisingMetricGroup(seller, cabinetId, metricName, sortedPeriods, request);
        }
        return getFunnelMetricGroup(seller, cabinetId, metricName, sortedPeriods, request);
    }

    private MetricGroupResponseDto getFunnelMetricGroup(
            User seller,
            Long cabinetId,
            String metricName,
            List<PeriodDto> periods,
            AnalyticsSummaryRequest request
    ) {
        List<WbProductCard> visibleCards = articleCatalogQuery.applyCatalogFilters(
                articleCatalogQuery.getVisibleCards(seller.getId(), cabinetId, request.getExcludedNmIds()),
                seller.getId(),
                cabinetId,
                request.getOnlyWithPhoto(),
                request.getOnlyPriority(),
                request.getOnlyInAdvertising()
        );

        List<ArticleMetricDto> articleMetrics = visibleCards.stream()
                .map(card -> calculateArticleMetric(card, metricName, periods, seller.getId(), cabinetId, null))
                .collect(Collectors.toList());

        return MetricGroupResponseDto.builder()
                .metricName(metricName)
                .metricNameRu(MetricNames.getRussianName(metricName))
                .category(AnalyticsPercentChange.metricCategory(metricName))
                .articles(articleMetrics)
                .build();
    }

    private MetricGroupResponseDto getAdvertisingMetricGroup(
            User seller,
            Long cabinetId,
            String metricName,
            List<PeriodDto> periods,
            AnalyticsSummaryRequest request
    ) {
        List<WbProductCard> visibleCards = articleCatalogQuery.applyCatalogFilters(
                articleCatalogQuery.getVisibleCards(seller.getId(), cabinetId, request.getExcludedNmIds()),
                seller.getId(),
                cabinetId,
                request.getOnlyWithPhoto(),
                request.getOnlyPriority(),
                request.getOnlyInAdvertising()
        );
        List<Long> campaignIds = campaignAnalyticsQuery.getCampaignIdsForCabinet(seller.getId(), cabinetId);

        Map<PeriodDto, Map<Long, WbCampaignStatisticsAggregator.AdvertisingStats>> statsByPeriodByArticle = new HashMap<>();
        for (PeriodDto period : periods) {
            statsByPeriodByArticle.put(period, campaignStatisticsAggregator.aggregateStatsByArticle(campaignIds, period));
        }
        Map<PeriodDto, Map<Long, BigDecimal>> funnelOrdersAmountByPeriodByArticle =
                MetricNames.DRR.equals(metricName)
                        ? preloadFunnelOrdersAmountByArticle(visibleCards, cabinetId, periods)
                        : Collections.emptyMap();

        List<ArticleMetricDto> articleMetrics = new ArrayList<>();
        WbCampaignStatisticsAggregator.AdvertisingStats emptyStats = new WbCampaignStatisticsAggregator.AdvertisingStats(
                0, 0, BigDecimal.ZERO, 0, BigDecimal.ZERO);

        for (WbProductCard card : visibleCards) {
            Long nmId = card.getNmId();
            if (nmId == null) {
                continue;
            }

            List<PeriodMetricValueDto> periodValues = new ArrayList<>();
            for (PeriodDto period : periods) {
                WbCampaignStatisticsAggregator.AdvertisingStats stats = statsByPeriodByArticle
                        .getOrDefault(period, Collections.emptyMap())
                        .getOrDefault(nmId, emptyStats);
                Object value = calculateAdvertisingMetricValue(
                        metricName, stats, period, nmId, funnelOrdersAmountByPeriodByArticle);
                BigDecimal changePercent = calculateArticleAdvertisingChangePercent(
                        nmId, metricName, period, periods, statsByPeriodByArticle, funnelOrdersAmountByPeriodByArticle);

                periodValues.add(PeriodMetricValueDto.builder()
                        .periodId(period.getId())
                        .value(value)
                        .changePercent(changePercent)
                        .build());
            }

            articleMetrics.add(ArticleMetricDto.builder()
                    .nmId(nmId)
                    .photoTm(card.getPhotoTm())
                    .periods(periodValues)
                    .build());
        }

        return MetricGroupResponseDto.builder()
                .metricName(metricName)
                .metricNameRu(MetricNames.getRussianName(metricName))
                .category(AnalyticsPercentChange.metricCategory(metricName))
                .articles(articleMetrics)
                .campaigns(Collections.emptyList())
                .build();
    }

    private Map<Integer, AggregatedMetricsDto> calculateAggregatedMetrics(
            List<WbProductCard> cards,
            List<PeriodDto> periods,
            Long sellerId,
            Long cabinetId
    ) {
        Map<Integer, AggregatedMetricsDto> result = new HashMap<>();
        Set<Long> nmIdsFilter = cards.stream()
                .map(WbProductCard::getNmId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        for (PeriodDto period : periods) {
            AggregatedMetricsDto metrics = new AggregatedMetricsDto();
            funnelMetricsCalculator.calculateFunnelMetrics(metrics, cards, period);
            advertisingMetricsCalculator.calculateAdvertisingMetrics(metrics, sellerId, cabinetId, period, nmIdsFilter);
            result.put(period.getId(), metrics);
        }
        return result;
    }

    private ArticleMetricDto calculateArticleMetric(
            WbProductCard card,
            String metricName,
            List<PeriodDto> periods,
            Long sellerId,
            Long cabinetId,
            Map<PeriodDto, WbCampaignStatisticsAggregator.AdvertisingStats> advertisingStatsCache
    ) {
        List<PeriodMetricValueDto> periodValues = periods.stream()
                .map(period -> {
                    Object value = metricValueCalculator.calculateValue(
                            card, metricName, period, sellerId, cabinetId, advertisingStatsCache);
                    PeriodDto previousPeriod = AnalyticsPercentChange.findPreviousPeriodByDateOrder(period, periods);
                    Object previousValue = previousPeriod == null
                            ? null
                            : metricValueCalculator.calculateValue(
                            card, metricName, previousPeriod, sellerId, cabinetId, advertisingStatsCache);
                    return PeriodMetricValueDto.builder()
                            .periodId(period.getId())
                            .value(value)
                            .changePercent(AnalyticsPercentChange.between(metricName, value, previousValue))
                            .build();
                })
                .collect(Collectors.toList());

        return ArticleMetricDto.builder()
                .nmId(card.getNmId())
                .photoTm(card.getPhotoTm())
                .periods(periodValues)
                .build();
    }

    private BigDecimal calculateArticleAdvertisingChangePercent(
            Long nmId,
            String metricName,
            PeriodDto period,
            List<PeriodDto> allPeriodsSortedByDate,
            Map<PeriodDto, Map<Long, WbCampaignStatisticsAggregator.AdvertisingStats>> statsByPeriodByArticle,
            Map<PeriodDto, Map<Long, BigDecimal>> funnelOrdersAmountByPeriodByArticle
    ) {
        PeriodDto previousPeriod = AnalyticsPercentChange.findPreviousPeriodByDateOrder(period, allPeriodsSortedByDate);
        if (previousPeriod == null) {
            return null;
        }
        WbCampaignStatisticsAggregator.AdvertisingStats empty =
                new WbCampaignStatisticsAggregator.AdvertisingStats(0, 0, BigDecimal.ZERO, 0, BigDecimal.ZERO);
        WbCampaignStatisticsAggregator.AdvertisingStats currentStats = statsByPeriodByArticle
                .getOrDefault(period, Collections.emptyMap())
                .getOrDefault(nmId, empty);
        WbCampaignStatisticsAggregator.AdvertisingStats previousStats = statsByPeriodByArticle
                .getOrDefault(previousPeriod, Collections.emptyMap())
                .getOrDefault(nmId, empty);
        Object currentValue = calculateAdvertisingMetricValue(
                metricName, currentStats, period, nmId, funnelOrdersAmountByPeriodByArticle);
        Object previousValue = calculateAdvertisingMetricValue(
                metricName, previousStats, previousPeriod, nmId, funnelOrdersAmountByPeriodByArticle);
        return AnalyticsPercentChange.between(metricName, currentValue, previousValue);
    }

    private Object calculateAdvertisingMetricValue(
            String metricName,
            WbCampaignStatisticsAggregator.AdvertisingStats stats,
            PeriodDto period,
            Long nmId,
            Map<PeriodDto, Map<Long, BigDecimal>> funnelOrdersAmountByPeriodByArticle
    ) {
        if (MetricNames.DRR.equals(metricName)) {
            BigDecimal costs = stats.sum();
            if (costs == null || costs.compareTo(BigDecimal.ZERO) == 0) {
                return null;
            }
            BigDecimal ordersAmount = funnelOrdersAmountByPeriodByArticle
                    .getOrDefault(period, Collections.emptyMap())
                    .get(nmId);
            if (ordersAmount != null && ordersAmount.compareTo(BigDecimal.ZERO) > 0) {
                return MathUtils.calculatePercentage(costs, ordersAmount);
            }
        }
        return calculateAdvertisingMetricValue(metricName, stats);
    }

    private Object calculateAdvertisingMetricValue(String metricName, WbCampaignStatisticsAggregator.AdvertisingStats stats) {
        return switch (metricName) {
            case MetricNames.VIEWS -> stats.views();
            case MetricNames.CLICKS -> stats.clicks();
            case MetricNames.COSTS -> stats.sum();
            case MetricNames.CPC -> {
                if (stats.clicks() == 0) {
                    yield null;
                }
                yield stats.sum().divide(BigDecimal.valueOf(stats.clicks()), 2, RoundingMode.HALF_UP);
            }
            case MetricNames.CTR -> MathUtils.calculatePercentage(stats.clicks(), stats.views());
            case MetricNames.CPO -> {
                if (stats.orders() == 0) {
                    yield null;
                }
                yield stats.sum().divide(BigDecimal.valueOf(stats.orders()), 2, RoundingMode.HALF_UP);
            }
            case MetricNames.DRR -> {
                if (stats.sum().compareTo(BigDecimal.ZERO) == 0 || stats.ordersSum().compareTo(BigDecimal.ZERO) == 0) {
                    yield null;
                }
                yield MathUtils.calculatePercentage(stats.sum(), stats.ordersSum());
            }
            default -> null;
        };
    }

    private Map<PeriodDto, Map<Long, BigDecimal>> preloadFunnelOrdersAmountByArticle(
            List<WbProductCard> cards,
            Long cabinetId,
            List<PeriodDto> periods
    ) {
        List<Long> nmIds = cards.stream()
                .map(WbProductCard::getNmId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (nmIds.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<PeriodDto, Map<Long, BigDecimal>> result = new HashMap<>();
        for (PeriodDto period : periods) {
            List<WbProductCardAnalytics> analytics = cabinetId != null
                    ? analyticsRepository.findByCabinet_IdAndProductCardNmIdInAndDateBetween(
                    cabinetId, nmIds, period.getDateFrom(), period.getDateTo())
                    : analyticsRepository.findByProductCardNmIdInAndDateBetween(
                    nmIds, period.getDateFrom(), period.getDateTo());
            Map<Long, BigDecimal> ordersAmountByNmId = new HashMap<>();
            for (WbProductCardAnalytics item : analytics) {
                if (item.getProductCard() == null || item.getProductCard().getNmId() == null) {
                    continue;
                }
                Long nmId = item.getProductCard().getNmId();
                BigDecimal amount = item.getOrdersSum() != null ? item.getOrdersSum() : BigDecimal.ZERO;
                ordersAmountByNmId.merge(nmId, amount, BigDecimal::add);
            }
            result.put(period, ordersAmountByNmId);
        }
        return result;
    }
}
