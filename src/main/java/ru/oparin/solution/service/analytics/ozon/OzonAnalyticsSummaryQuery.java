package ru.oparin.solution.service.analytics.ozon;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.oparin.solution.dto.analytics.*;
import ru.oparin.solution.model.OzonProductCard;
import ru.oparin.solution.model.OzonProductCardAnalytics;
import ru.oparin.solution.model.OzonPromotionCampaignProductStatistics;
import ru.oparin.solution.repository.OzonProductCardAnalyticsRepository;
import ru.oparin.solution.repository.OzonPromotionCampaignProductStatisticsRepository;
import ru.oparin.solution.service.analytics.AnalyticsPercentChange;
import ru.oparin.solution.service.analytics.CatalogPageHelper;
import ru.oparin.solution.service.analytics.MetricNames;
import ru.oparin.solution.service.analytics.OzonSummaryMetricsCalculator;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Сводная аналитика и группа метрик Ozon.
 */
@Service
@RequiredArgsConstructor
public class OzonAnalyticsSummaryQuery {

    private final OzonArticleCatalogQuery articleCatalogQuery;
    private final OzonSummaryMetricsCalculator ozonSummaryMetricsCalculator;
    private final OzonProductCardAnalyticsRepository ozonProductCardAnalyticsRepository;
    private final OzonPromotionCampaignProductStatisticsRepository ozonPromotionCampaignProductStatisticsRepository;

    /**
     * Сводная по товарам Ozon: страница или полный список с агрегатами.
     */
    @Transactional(readOnly = true)
    public SummaryResponseDto getSummary(Long cabinetId, AnalyticsSummaryRequest request) {
        List<PeriodDto> periods = AnalyticsPercentChange.sortPeriodsByDateFrom(request.getPeriods());
        List<OzonProductCard> visibleCards = articleCatalogQuery.getVisibleCards(
                cabinetId, request.getExcludedNmIds(), request.getOnlyWithPhoto());
        if (Boolean.TRUE.equals(request.getOnlyInAdvertising())) {
            visibleCards = articleCatalogQuery.applyOnlyInAdvertising(visibleCards, cabinetId);
        }
        ArticleSummarySortField resolvedSortBy = ArticleSummarySortField.fromParam(request.getSortBy());
        Sort.Direction resolvedSortDir = Sort.Direction.fromOptionalString(request.getSortDir()).orElse(Sort.Direction.DESC);

        if (CatalogPageHelper.isPaginated(request.getPage(), request.getSize())) {
            List<OzonProductCard> filtered = CatalogPageHelper.applyInclusionFilter(
                    visibleCards, request.getFilterToNone(), request.getIncludedNmIds(), OzonProductCard::getProductId);
            CatalogPageHelper.Slice<OzonProductCard> slice = CatalogPageHelper.filterSearchSortAndPaginate(
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
                    .periods(periods)
                    .articles(articleCatalogQuery.mapToArticleSummaries(slice.items(), cabinetId))
                    .aggregatedMetrics(null)
                    .totalArticles((long) slice.total())
                    .build();
        }

        Set<Long> productIds = visibleCards.stream()
                .map(OzonProductCard::getProductId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, List<OzonProductCardAnalytics>> analyticsByProductId = loadAnalyticsGroupedByProduct(
                cabinetId, productIds, periods);
        Map<Integer, AggregatedMetricsDto> aggregatedMetrics = ozonSummaryMetricsCalculator.calculateAggregatedMetrics(
                cabinetId, productIds, analyticsByProductId, periods);
        List<OzonProductCard> sortedCards = new ArrayList<>(visibleCards);
        articleCatalogQuery.sortProductCards(sortedCards, resolvedSortBy, resolvedSortDir);
        return SummaryResponseDto.builder()
                .periods(periods)
                .articles(articleCatalogQuery.mapToArticleSummaries(sortedCards, cabinetId))
                .aggregatedMetrics(aggregatedMetrics)
                .totalArticles(null)
                .build();
    }

    /**
     * Детальные значения одной метрики по видимым товарам Ozon.
     */
    @Transactional(readOnly = true)
    public MetricGroupResponseDto getMetricGroup(
            Long cabinetId,
            String metricName,
            AnalyticsSummaryRequest request
    ) {
        List<PeriodDto> periods = AnalyticsPercentChange.sortPeriodsByDateFrom(request.getPeriods());
        List<OzonProductCard> visibleCards = articleCatalogQuery.getVisibleCards(
                cabinetId, request.getExcludedNmIds(), request.getOnlyWithPhoto());
        Map<Long, List<OzonProductCardAnalytics>> analyticsByProductId = loadAnalyticsGroupedByProduct(
                cabinetId,
                visibleCards.stream().map(OzonProductCard::getProductId).filter(Objects::nonNull).collect(Collectors.toSet()),
                periods
        );
        LocalDate minFrom = periods.stream().map(PeriodDto::getDateFrom).min(LocalDate::compareTo).orElseThrow();
        LocalDate maxTo = periods.stream().map(PeriodDto::getDateTo).max(LocalDate::compareTo).orElseThrow();
        List<OzonPromotionCampaignProductStatistics> adRows =
                ozonPromotionCampaignProductStatisticsRepository.findByCampaign_Cabinet_IdAndDateBetween(
                        cabinetId, minFrom, maxTo);

        List<ArticleMetricDto> articleMetrics = visibleCards.stream()
                .map(card -> buildArticleMetric(card, metricName, periods, analyticsByProductId, adRows))
                .collect(Collectors.toList());

        return MetricGroupResponseDto.builder()
                .metricName(metricName)
                .metricNameRu(MetricNames.getRussianName(metricName))
                .category(AnalyticsPercentChange.metricCategory(metricName))
                .articles(articleMetrics)
                .build();
    }

    private ArticleMetricDto buildArticleMetric(
            OzonProductCard card,
            String metricName,
            List<PeriodDto> periods,
            Map<Long, List<OzonProductCardAnalytics>> analyticsByProductId,
            List<OzonPromotionCampaignProductStatistics> adRows
    ) {
        List<OzonProductCardAnalytics> rows = analyticsByProductId.getOrDefault(card.getProductId(), List.of());
        List<PeriodMetricValueDto> periodValues = new ArrayList<>();
        for (PeriodDto period : periods) {
            Object value = ozonSummaryMetricsCalculator.calculateArticleValue(
                    metricName, card, period, rows, adRows);
            PeriodDto previousPeriod = AnalyticsPercentChange.findPreviousPeriodByDateOrder(period, periods);
            Object previousValue = previousPeriod == null
                    ? null
                    : ozonSummaryMetricsCalculator.calculateArticleValue(metricName, card, previousPeriod, rows, adRows);
            periodValues.add(PeriodMetricValueDto.builder()
                    .periodId(period.getId())
                    .value(value)
                    .changePercent(AnalyticsPercentChange.between(metricName, value, previousValue))
                    .build());
        }
        return ArticleMetricDto.builder()
                .nmId(card.getProductId())
                .photoTm(card.getPhotoUrl())
                .periods(periodValues)
                .build();
    }

    private Map<Long, List<OzonProductCardAnalytics>> loadAnalyticsGroupedByProduct(
            Long cabinetId,
            Set<Long> productIds,
            List<PeriodDto> periods
    ) {
        if (productIds.isEmpty() || periods.isEmpty()) {
            return Map.of();
        }
        LocalDate dateFrom = periods.stream()
                .map(PeriodDto::getDateFrom)
                .min(LocalDate::compareTo)
                .orElseThrow();
        LocalDate dateTo = periods.stream()
                .map(PeriodDto::getDateTo)
                .max(LocalDate::compareTo)
                .orElseThrow();
        return ozonProductCardAnalyticsRepository.findByCabinet_IdAndDateBetween(cabinetId, dateFrom, dateTo).stream()
                .filter(row -> productIds.contains(row.getProductId()))
                .collect(Collectors.groupingBy(OzonProductCardAnalytics::getProductId));
    }
}
