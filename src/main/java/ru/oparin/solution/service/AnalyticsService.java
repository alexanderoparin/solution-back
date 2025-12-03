package ru.oparin.solution.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.oparin.solution.dto.analytics.*;
import ru.oparin.solution.model.ProductCard;
import ru.oparin.solution.model.User;
import ru.oparin.solution.repository.ProductCardAnalyticsRepository;
import ru.oparin.solution.repository.ProductCardRepository;
import ru.oparin.solution.repository.PromotionCampaignRepository;
import ru.oparin.solution.model.PromotionCampaign;
import ru.oparin.solution.service.analytics.AdvertisingMetricsCalculator;
import ru.oparin.solution.service.analytics.CampaignStatisticsAggregator;
import ru.oparin.solution.service.analytics.FunnelMetricsCalculator;
import ru.oparin.solution.service.analytics.MathUtils;
import ru.oparin.solution.service.analytics.MetricNames;
import ru.oparin.solution.service.analytics.MetricValueCalculator;
import ru.oparin.solution.service.analytics.ProductCardFilter;
import ru.oparin.solution.util.PeriodGenerator;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Сервис для работы с аналитикой.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AnalyticsService {

    private final ProductCardRepository productCardRepository;
    private final ProductCardAnalyticsRepository analyticsRepository;
    private final PromotionCampaignRepository campaignRepository;
    private final FunnelMetricsCalculator funnelMetricsCalculator;
    private final AdvertisingMetricsCalculator advertisingMetricsCalculator;
    private final MetricValueCalculator metricValueCalculator;
    private final CampaignStatisticsAggregator campaignStatisticsAggregator;


    /**
     * Получает сводную аналитику для продавца.
     */
    @Transactional(readOnly = true)
    public SummaryResponseDto getSummary(User seller, List<PeriodDto> periods, List<Long> excludedNmIds) {
        validatePeriods(periods);

        List<ProductCard> visibleCards = getVisibleCards(seller.getId(), excludedNmIds);
        Map<Integer, AggregatedMetricsDto> aggregatedMetrics = calculateAggregatedMetrics(
                visibleCards, periods, seller.getId());

        return SummaryResponseDto.builder()
                .periods(periods)
                .articles(mapToArticleSummaries(visibleCards))
                .aggregatedMetrics(aggregatedMetrics)
                .build();
    }

    /**
     * Получает детальные метрики по группе для всех артикулов.
     */
    @Transactional(readOnly = true)
    public MetricGroupResponseDto getMetricGroup(
            User seller,
            String metricName,
            List<PeriodDto> periods,
            List<Long> excludedNmIds
    ) {
        List<ProductCard> visibleCards = getVisibleCards(seller.getId(), excludedNmIds);
        
        // Для рекламных метрик кэшируем статистику по периодам, чтобы не делать повторные запросы
        final Map<PeriodDto, CampaignStatisticsAggregator.AdvertisingStats> advertisingStatsCache;
        if (isAdvertisingMetric(metricName)) {
            advertisingStatsCache = preloadAdvertisingStats(seller.getId(), periods);
        } else {
            advertisingStatsCache = null;
        }
        
        final Map<PeriodDto, CampaignStatisticsAggregator.AdvertisingStats> finalCache = advertisingStatsCache;
        List<ArticleMetricDto> articleMetrics = visibleCards.stream()
                .map(card -> calculateArticleMetric(card, metricName, periods, seller.getId(), finalCache))
                .collect(Collectors.toList());

        return MetricGroupResponseDto.builder()
                .metricName(metricName)
                .metricNameRu(MetricNames.getRussianName(metricName))
                .category(getMetricCategory(metricName))
                .articles(articleMetrics)
                .build();
    }
    
    private boolean isAdvertisingMetric(String metricName) {
        return metricName.equals(MetricNames.VIEWS) ||
               metricName.equals(MetricNames.CLICKS) ||
               metricName.equals(MetricNames.COSTS) ||
               metricName.equals(MetricNames.CPC) ||
               metricName.equals(MetricNames.CTR) ||
               metricName.equals(MetricNames.CPO) ||
               metricName.equals(MetricNames.DRR);
    }
    
    private Map<PeriodDto, CampaignStatisticsAggregator.AdvertisingStats> preloadAdvertisingStats(
            Long sellerId,
            List<PeriodDto> periods
    ) {
        Map<PeriodDto, CampaignStatisticsAggregator.AdvertisingStats> cache = new HashMap<>();
        List<Long> campaignIds = campaignRepository.findBySellerId(sellerId).stream()
                .map(PromotionCampaign::getAdvertId)
                .collect(Collectors.toList());
        
        for (PeriodDto period : periods) {
            CampaignStatisticsAggregator.AdvertisingStats stats = 
                    campaignStatisticsAggregator.aggregateStats(campaignIds, period);
            cache.put(period, stats);
        }
        
        return cache;
    }

    /**
     * Получает детальную информацию по артикулу.
     */
    @Transactional(readOnly = true)
    public ArticleResponseDto getArticle(User seller, Long nmId, List<PeriodDto> periods) {
        ProductCard card = findCardBySeller(nmId, seller.getId());

        return ArticleResponseDto.builder()
                .article(mapToArticleDetail(card))
                .periods(periods)
                .metrics(calculateAllMetrics(card, periods, seller.getId()))
                .dailyData(getDailyData(nmId))
                .campaigns(getCampaigns(seller.getId()))
                .build();
    }

    private void validatePeriods(List<PeriodDto> periods) {
        if (!PeriodGenerator.validatePeriods(periods)) {
            throw new IllegalArgumentException("Периоды пересекаются или некорректны");
        }
    }

    private List<ProductCard> getVisibleCards(Long sellerId, List<Long> excludedNmIds) {
        List<ProductCard> allCards = productCardRepository.findBySellerId(sellerId);
        return ProductCardFilter.filterVisibleCards(allCards, excludedNmIds);
    }

    private Map<Integer, AggregatedMetricsDto> calculateAggregatedMetrics(
            List<ProductCard> cards,
            List<PeriodDto> periods,
            Long sellerId
    ) {
        Map<Integer, AggregatedMetricsDto> result = new HashMap<>();

        for (PeriodDto period : periods) {
            AggregatedMetricsDto metrics = new AggregatedMetricsDto();
            funnelMetricsCalculator.calculateFunnelMetrics(metrics, cards, period);
            advertisingMetricsCalculator.calculateAdvertisingMetrics(metrics, sellerId, period);
            result.put(period.getId(), metrics);
        }

        return result;
    }

    private ArticleMetricDto calculateArticleMetric(
            ProductCard card,
            String metricName,
            List<PeriodDto> periods,
            Long sellerId,
            Map<PeriodDto, CampaignStatisticsAggregator.AdvertisingStats> advertisingStatsCache
    ) {
        List<PeriodMetricValueDto> periodValues = periods.stream()
                .map(period -> calculatePeriodMetricValue(card, metricName, period, periods, sellerId, advertisingStatsCache))
                .collect(Collectors.toList());

        return ArticleMetricDto.builder()
                .nmId(card.getNmId())
                .periods(periodValues)
                .build();
    }

    private PeriodMetricValueDto calculatePeriodMetricValue(
            ProductCard card,
            String metricName,
            PeriodDto period,
            List<PeriodDto> allPeriods,
            Long sellerId,
            Map<PeriodDto, CampaignStatisticsAggregator.AdvertisingStats> advertisingStatsCache
    ) {
        Object value = metricValueCalculator.calculateValue(card, metricName, period, sellerId, advertisingStatsCache);
        BigDecimal changePercent = calculateChangePercent(card, metricName, period, allPeriods, sellerId, value, advertisingStatsCache);

        return PeriodMetricValueDto.builder()
                .periodId(period.getId())
                .value(value)
                .changePercent(changePercent)
                .build();
    }

    private BigDecimal calculateChangePercent(
            ProductCard card,
            String metricName,
            PeriodDto period,
            List<PeriodDto> allPeriods,
            Long sellerId,
            Object currentValue,
            Map<PeriodDto, CampaignStatisticsAggregator.AdvertisingStats> advertisingStatsCache
    ) {
        if (period.getId() == 1 || currentValue == null) {
            return null;
        }

        PeriodDto previousPeriod = findPreviousPeriod(period, allPeriods);
        if (previousPeriod == null) {
            return null;
        }

        Object previousValue = metricValueCalculator.calculateValue(card, metricName, previousPeriod, sellerId, advertisingStatsCache);
        return calculatePercentageChange(currentValue, previousValue);
    }

    private PeriodDto findPreviousPeriod(PeriodDto currentPeriod, List<PeriodDto> allPeriods) {
        return allPeriods.stream()
                .filter(p -> p.getId() == currentPeriod.getId() - 1)
                .findFirst()
                .orElse(null);
    }

    private BigDecimal calculatePercentageChange(Object current, Object previous) {
        BigDecimal currentDecimal = convertToBigDecimal(current);
        BigDecimal previousDecimal = convertToBigDecimal(previous);
        return MathUtils.calculatePercentageChange(currentDecimal, previousDecimal);
    }

    private BigDecimal convertToBigDecimal(Object value) {
        if (value == null) {
            return null;
        }
        return switch (value) {
            case BigDecimal bd -> bd;
            case Integer i -> BigDecimal.valueOf(i);
            case Long l -> BigDecimal.valueOf(l);
            case Double d -> BigDecimal.valueOf(d);
            default -> null;
        };
    }

    private List<MetricDto> calculateAllMetrics(ProductCard card, List<PeriodDto> periods, Long sellerId) {
        // Для рекламных метрик кэшируем статистику по периодам
        Map<PeriodDto, CampaignStatisticsAggregator.AdvertisingStats> advertisingStatsCache = preloadAdvertisingStats(sellerId, periods);
        
        List<MetricDto> metrics = new ArrayList<>();

        for (String metricName : MetricNames.getAllMetrics()) {
            List<PeriodMetricValueDto> periodValues = periods.stream()
                    .map(period -> {
                        Object value = metricValueCalculator.calculateValue(card, metricName, period, sellerId, advertisingStatsCache);
                        BigDecimal changePercent = calculateChangePercent(
                                card, metricName, period, periods, sellerId, value, advertisingStatsCache);
                        return PeriodMetricValueDto.builder()
                                .periodId(period.getId())
                                .value(value)
                                .changePercent(changePercent)
                                .build();
                    })
                    .collect(Collectors.toList());

            metrics.add(MetricDto.builder()
                    .metricName(metricName)
                    .metricNameRu(MetricNames.getRussianName(metricName))
                    .category(getMetricCategory(metricName))
                    .periods(periodValues)
                    .build());
        }

        return metrics;
    }

    private List<DailyDataDto> getDailyData(Long nmId) {
        LocalDate endDate = LocalDate.now().minusDays(1);
        LocalDate startDate = endDate.minusDays(13);

        return analyticsRepository.findByProductCardNmIdAndDateBetween(nmId, startDate, endDate).stream()
                .sorted((a1, a2) -> a2.getDate().compareTo(a1.getDate()))
                .map(a -> DailyDataDto.builder()
                        .date(a.getDate())
                        .transitions(a.getOpenCard())
                        .cart(a.getAddToCart())
                        .orders(a.getOrders())
                        .build())
                .collect(Collectors.toList());
    }

    private List<CampaignDto> getCampaigns(Long sellerId) {
        return campaignRepository.findBySellerId(sellerId).stream()
                .map(c -> CampaignDto.builder()
                        .id(c.getAdvertId())
                        .name(c.getName())
                        .type(c.getType() != null ? c.getType().getDescription() : null)
                        .createdAt(c.getCreateTime())
                        .build())
                .collect(Collectors.toList());
    }

    private ProductCard findCardBySeller(Long nmId, Long sellerId) {
        ProductCard card = productCardRepository.findByNmId(nmId)
                .orElseThrow(() -> new IllegalArgumentException("Артикул не найден: " + nmId));

        if (!card.getSeller().getId().equals(sellerId)) {
            throw new IllegalArgumentException("Артикул не принадлежит продавцу");
        }

        return card;
    }

    private List<ArticleSummaryDto> mapToArticleSummaries(List<ProductCard> cards) {
        return cards.stream()
                .map(this::mapToArticleSummary)
                .collect(Collectors.toList());
    }

    private ArticleSummaryDto mapToArticleSummary(ProductCard card) {
        return ArticleSummaryDto.builder()
                .nmId(card.getNmId())
                .title(card.getTitle())
                .brand(card.getBrand())
                .subjectName(card.getSubjectName())
                .build();
    }

    private ArticleDetailDto mapToArticleDetail(ProductCard card) {
        return ArticleDetailDto.builder()
                .nmId(card.getNmId())
                .title(card.getTitle())
                .brand(card.getBrand())
                .subjectName(card.getSubjectName())
                .vendorCode(card.getVendorCode())
                .rating(null) // TODO: получить из API или БД
                .reviewsCount(null) // TODO: получить из API или БД
                .productUrl("https://www.wildberries.ru/catalog/" + card.getNmId() + "/detail.aspx")
                .build();
    }

    private String getMetricCategory(String metricName) {
        if (MetricNames.isFunnelMetric(metricName)) {
            return "funnel";
        }
        if (MetricNames.isAdvertisingMetric(metricName)) {
            return "advertising";
        }
        return "unknown";
    }
}
