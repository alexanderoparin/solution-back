package ru.oparin.solution.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.oparin.solution.dto.analytics.*;
import ru.oparin.solution.model.ProductCard;
import ru.oparin.solution.model.User;
import ru.oparin.solution.repository.CampaignArticleRepository;
import ru.oparin.solution.repository.ProductCardAnalyticsRepository;
import ru.oparin.solution.repository.ProductCardRepository;
import ru.oparin.solution.repository.ProductPriceHistoryRepository;
import ru.oparin.solution.repository.ProductStockRepository;
import ru.oparin.solution.repository.PromotionCampaignRepository;
import ru.oparin.solution.repository.PromotionCampaignStatisticsRepository;
import ru.oparin.solution.repository.WbWarehouseRepository;
import ru.oparin.solution.model.CampaignArticle;
import ru.oparin.solution.model.CampaignStatus;
import ru.oparin.solution.model.ProductCardAnalytics;
import ru.oparin.solution.model.ProductPriceHistory;
import ru.oparin.solution.model.PromotionCampaign;
import ru.oparin.solution.model.PromotionCampaignStatistics;
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
import java.util.*;
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
    private final CampaignArticleRepository campaignArticleRepository;
    private final PromotionCampaignStatisticsRepository campaignStatisticsRepository;
    private final ProductPriceHistoryRepository priceHistoryRepository;
    private final ProductStockRepository stockRepository;
    private final WbWarehouseRepository warehouseRepository;
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
     * Получает детальные метрики по группе для всех артикулов (воронка) или кампаний (реклама).
     */
    @Transactional(readOnly = true)
    public MetricGroupResponseDto getMetricGroup(
            User seller,
            String metricName,
            List<PeriodDto> periods,
            List<Long> excludedNmIds
    ) {
        if (isAdvertisingMetric(metricName)) {
            // Для рекламных метрик группируем по кампаниям
            return getAdvertisingMetricGroup(seller, metricName, periods);
        } else {
            // Для метрик воронки группируем по артикулам
            return getFunnelMetricGroup(seller, metricName, periods, excludedNmIds);
        }
    }

    private MetricGroupResponseDto getFunnelMetricGroup(
            User seller,
            String metricName,
            List<PeriodDto> periods,
            List<Long> excludedNmIds
    ) {
        List<ProductCard> visibleCards = getVisibleCards(seller.getId(), excludedNmIds);
        
        List<ArticleMetricDto> articleMetrics = visibleCards.stream()
                .map(card -> calculateArticleMetric(card, metricName, periods, seller.getId(), null))
                .collect(Collectors.toList());

        return MetricGroupResponseDto.builder()
                .metricName(metricName)
                .metricNameRu(MetricNames.getRussianName(metricName))
                .category(getMetricCategory(metricName))
                .articles(articleMetrics)
                .build();
    }

    private MetricGroupResponseDto getAdvertisingMetricGroup(
            User seller,
            String metricName,
            List<PeriodDto> periods
    ) {
        // Получаем все кампании продавца
        List<PromotionCampaign> campaigns = campaignRepository.findBySellerId(seller.getId());
        
        List<CampaignMetricDto> campaignMetrics = new ArrayList<>();
        
        for (PromotionCampaign campaign : campaigns) {
            // Получаем артикулы для кампании
            List<CampaignArticle> campaignArticles = campaignArticleRepository.findByCampaignId(campaign.getAdvertId());
            List<Long> articleIds = campaignArticles.stream()
                    .map(CampaignArticle::getNmId)
                    .collect(Collectors.toList());
            
            // Если нет артикулов, пропускаем кампанию
            if (articleIds.isEmpty()) {
                continue;
            }
            
            // Рассчитываем метрику для каждого периода
            List<PeriodMetricValueDto> periodValues = new ArrayList<>();
            boolean hasAnyData = false;
            
            for (PeriodDto period : periods) {
                CampaignStatisticsAggregator.AdvertisingStats stats = 
                        campaignStatisticsAggregator.aggregateStatsForCampaign(campaign.getAdvertId(), period);
                
                Object value = calculateAdvertisingMetricValue(metricName, stats);
                BigDecimal changePercent = calculateCampaignChangePercent(
                        campaign.getAdvertId(), metricName, period, periods);
                
                // Проверяем, есть ли данные (не null и не 0)
                if (value != null && !isZero(value)) {
                    hasAnyData = true;
                }
                
                periodValues.add(PeriodMetricValueDto.builder()
                        .periodId(period.getId())
                        .value(value)
                        .changePercent(changePercent)
                        .build());
            }
            
            // Добавляем кампанию только если есть данные хотя бы по одному периоду
            if (hasAnyData) {
                campaignMetrics.add(CampaignMetricDto.builder()
                        .campaignId(campaign.getAdvertId())
                        .campaignName(campaign.getName())
                        .articles(articleIds)
                        .periods(periodValues)
                        .build());
            }
        }

        return MetricGroupResponseDto.builder()
                .metricName(metricName)
                .metricNameRu(MetricNames.getRussianName(metricName))
                .category(getMetricCategory(metricName))
                .campaigns(campaignMetrics)
                .build();
    }

    private Object calculateAdvertisingMetricValue(String metricName, CampaignStatisticsAggregator.AdvertisingStats stats) {
        return switch (metricName) {
            case MetricNames.VIEWS -> stats.views();
            case MetricNames.CLICKS -> stats.clicks();
            case MetricNames.COSTS -> stats.sum();
            case MetricNames.CPC -> {
                if (stats.clicks() == 0) {
                    yield null;
                }
                yield stats.sum().divide(BigDecimal.valueOf(stats.clicks()), 2, java.math.RoundingMode.HALF_UP);
            }
            case MetricNames.CTR -> MathUtils.calculatePercentage(stats.clicks(), stats.views());
            case MetricNames.CPO -> {
                if (stats.orders() == 0) {
                    yield null;
                }
                yield stats.sum().divide(BigDecimal.valueOf(stats.orders()), 2, java.math.RoundingMode.HALF_UP);
            }
            case MetricNames.DRR -> {
                if (stats.sum().compareTo(BigDecimal.ZERO) == 0 || stats.ordersSum().compareTo(BigDecimal.ZERO) == 0) {
                    yield null;
                }
                // ДРР (доля рекламных расходов) = (расходы / сумма заказов) * 100
                yield MathUtils.calculatePercentage(stats.sum(), stats.ordersSum());
            }
            default -> null;
        };
    }

    private boolean isZero(Object value) {
        if (value == null) {
            return true;
        }
        if (value instanceof Number) {
            return ((Number) value).doubleValue() == 0.0;
        }
        return false;
    }

    private BigDecimal calculateCampaignChangePercent(
            Long campaignId,
            String metricName,
            PeriodDto period,
            List<PeriodDto> allPeriods
    ) {
        if (period.getId() == 1) {
            return null;
        }

        PeriodDto previousPeriod = findPreviousPeriod(period, allPeriods);
        if (previousPeriod == null) {
            return null;
        }

        CampaignStatisticsAggregator.AdvertisingStats currentStats = 
                campaignStatisticsAggregator.aggregateStatsForCampaign(campaignId, period);
        CampaignStatisticsAggregator.AdvertisingStats previousStats = 
                campaignStatisticsAggregator.aggregateStatsForCampaign(campaignId, previousPeriod);

        Object currentValue = calculateAdvertisingMetricValue(metricName, currentStats);
        Object previousValue = calculateAdvertisingMetricValue(metricName, previousStats);

        // Для процентных метрик вычисляем разницу, для остальных - процентное изменение
        if (MetricNames.isPercentageMetric(metricName)) {
            return calculatePercentageDifference(currentValue, previousValue);
        } else {
            return calculatePercentageChange(currentValue, previousValue);
        }
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
                .campaigns(getCampaigns(nmId))
                .stocks(getStocks(nmId))
                .build();
    }

    private void validatePeriods(List<PeriodDto> periods) {
        if (!PeriodGenerator.validatePeriods(periods)) {
            throw new IllegalArgumentException("Периоды некорректны: дата начала периода не может быть позже даты окончания");
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
                .photoTm(card.getPhotoTm())
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
        
        // Для процентных метрик вычисляем разницу, для остальных - процентное изменение
        if (MetricNames.isPercentageMetric(metricName)) {
            return calculatePercentageDifference(currentValue, previousValue);
        } else {
            return calculatePercentageChange(currentValue, previousValue);
        }
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

    /**
     * Вычисляет разницу между двумя процентными значениями.
     * Используется для метрик, измеряемых в процентах (конверсия, CTR, DRR).
     */
    private BigDecimal calculatePercentageDifference(Object current, Object previous) {
        BigDecimal currentDecimal = convertToBigDecimal(current);
        BigDecimal previousDecimal = convertToBigDecimal(previous);
        return MathUtils.calculatePercentageDifference(currentDecimal, previousDecimal);
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

        // Получаем данные воронки
        List<ProductCardAnalytics> funnelData = analyticsRepository.findByProductCardNmIdAndDateBetween(nmId, startDate, endDate);
        
        // Получаем рекламные данные
        List<PromotionCampaignStatistics> advertisingData = campaignStatisticsRepository.findByNmIdAndDateBetween(nmId, startDate, endDate);
        
        // Получаем данные ценообразования
        List<ProductPriceHistory> priceData = priceHistoryRepository.findByNmIdAndDateBetween(nmId, startDate, endDate);
        
        // Группируем рекламные данные по датам
        Map<LocalDate, AdvertisingDailyStats> advertisingByDate = advertisingData.stream()
                .collect(Collectors.groupingBy(
                        PromotionCampaignStatistics::getDate,
                        Collectors.collectingAndThen(
                                Collectors.toList(),
                                stats -> aggregateAdvertisingStats(stats)
                        )
                ));
        
        // Группируем данные ценообразования по датам (берем запись без размера или первую)
        Map<LocalDate, ProductPriceHistory> priceByDate = priceData.stream()
                .collect(Collectors.groupingBy(
                        ProductPriceHistory::getDate,
                        Collectors.collectingAndThen(
                                Collectors.toList(),
                                prices -> {
                                    // Сначала ищем запись без размера
                                    Optional<ProductPriceHistory> withoutSize = prices.stream()
                                            .filter(p -> p.getSizeId() == null)
                                            .findFirst();
                                    if (withoutSize.isPresent()) {
                                        return withoutSize.get();
                                    }
                                    // Если нет, берем первую
                                    return prices.get(0);
                                }
                        )
                ));

        // Создаем мапу для быстрого поиска данных воронки
        Map<LocalDate, ProductCardAnalytics> funnelByDate = funnelData.stream()
                .collect(Collectors.toMap(
                        ProductCardAnalytics::getDate,
                        a -> a,
                        (a1, a2) -> a1 // Если есть дубликаты, берем первый
                ));

        // Создаем список всех дат
        List<LocalDate> allDates = new ArrayList<>();
        LocalDate currentDate = startDate;
        while (!currentDate.isAfter(endDate)) {
            allDates.add(currentDate);
            currentDate = currentDate.plusDays(1);
        }

        // Собираем результат
        return allDates.stream()
                .map(date -> {
                    ProductCardAnalytics funnel = funnelByDate.get(date);
                    AdvertisingDailyStats advertising = advertisingByDate.get(date);
                    
                    DailyDataDto.DailyDataDtoBuilder builder = DailyDataDto.builder()
                            .date(date);
                    
                    if (funnel != null) {
                        builder.transitions(funnel.getOpenCard())
                                .cart(funnel.getAddToCart())
                                .orders(funnel.getOrders())
                                .ordersAmount(funnel.getOrdersSum())
                                .cartConversion(calculateCartConversion(funnel.getOpenCard(), funnel.getAddToCart()))
                                .orderConversion(calculateOrderConversion(funnel.getAddToCart(), funnel.getOrders()));
                    }
                    
                    if (advertising != null) {
                        builder.views(advertising.views)
                                .clicks(advertising.clicks)
                                .costs(advertising.costs)
                                .cpc(advertising.cpc)
                                .ctr(advertising.ctr)
                                .cpo(advertising.cpo)
                                .drr(advertising.drr);
                    }
                    
                    ProductPriceHistory price = priceByDate.get(date);
                    if (price != null) {
                        builder.priceBeforeDiscount(price.getPrice())
                                .sellerDiscount(price.getDiscount())
                                .priceWithDiscount(price.getDiscountedPrice())
                                .wbClubDiscount(price.getClubDiscount())
                                .priceWithWbClub(price.getClubDiscountedPrice());
                        
                        // Расчет СПП (Скидка постоянного покупателя)
                        // СПП - это скидка, которую дает сам Wildberries постоянным покупателям
                        if (price.getSppPrice() != null) {
                            builder.priceWithSpp(price.getSppPrice());
                            
                            // СПП (руб) = Цена со скидкой WB Клуба - Цена с СПП
                            if (price.getClubDiscountedPrice() != null && price.getSppPrice() != null) {
                                BigDecimal sppAmount = price.getClubDiscountedPrice().subtract(price.getSppPrice());
                                builder.sppAmount(sppAmount);
                                
                                // СПП (%) = (СПП руб / Цена со скидкой WB Клуба) * 100
                                BigDecimal sppPercent = MathUtils.calculatePercentage(sppAmount, price.getClubDiscountedPrice());
                                builder.sppPercent(sppPercent);
                            }
                        }
                    }
                    
                    return builder.build();
                })
                .collect(Collectors.toList());
    }

    /**
     * Рассчитывает конверсию из переходов в корзину.
     * Формула: (addToCart / openCard) * 100
     *
     * @param openCard количество переходов в карточку
     * @param addToCart количество добавлений в корзину
     * @return конверсия в процентах или null, если нет данных
     */
    private BigDecimal calculateCartConversion(Integer openCard, Integer addToCart) {
        if (openCard == null || addToCart == null || openCard == 0) {
            return null;
        }
        return MathUtils.calculatePercentage(addToCart, openCard);
    }

    /**
     * Рассчитывает конверсию из корзины в заказ.
     * Формула: (orders / addToCart) * 100
     *
     * @param addToCart количество добавлений в корзину
     * @param orders количество заказов
     * @return конверсия в процентах или null, если нет данных
     */
    private BigDecimal calculateOrderConversion(Integer addToCart, Integer orders) {
        if (addToCart == null || orders == null || addToCart == 0) {
            return null;
        }
        return MathUtils.calculatePercentage(orders, addToCart);
    }

    private AdvertisingDailyStats aggregateAdvertisingStats(List<PromotionCampaignStatistics> stats) {
        int views = 0;
        int clicks = 0;
        BigDecimal sum = BigDecimal.ZERO;
        int orders = 0;
        BigDecimal ordersSum = BigDecimal.ZERO;
        
        for (PromotionCampaignStatistics stat : stats) {
            if (stat.getViews() != null) views += stat.getViews();
            if (stat.getClicks() != null) clicks += stat.getClicks();
            if (stat.getSum() != null) sum = sum.add(stat.getSum());
            if (stat.getOrders() != null) orders += stat.getOrders();
            if (stat.getOrdersSum() != null) ordersSum = ordersSum.add(stat.getOrdersSum());
        }
        
        BigDecimal costs = sum;
        BigDecimal cpc = MathUtils.divideSafely(sum, BigDecimal.valueOf(clicks));
        BigDecimal ctr = MathUtils.calculatePercentage(clicks, views);
        BigDecimal cpo = MathUtils.divideSafely(sum, BigDecimal.valueOf(orders));
        // ДРР (доля рекламных расходов) = (расходы / сумма заказов) * 100
        BigDecimal drr = MathUtils.calculatePercentage(sum, ordersSum);
        
        return new AdvertisingDailyStats(views, clicks, costs, cpc, ctr, cpo, drr);
    }

    private record AdvertisingDailyStats(
            Integer views,
            Integer clicks,
            BigDecimal costs,
            BigDecimal cpc,
            BigDecimal ctr,
            BigDecimal cpo,
            BigDecimal drr
    ) {}

    private List<CampaignDto> getCampaigns(Long nmId) {
        // Находим все кампании, в которых участвует этот артикул
        List<CampaignArticle> campaignArticles = campaignArticleRepository.findByNmId(nmId);
        
        return campaignArticles.stream()
                .map(CampaignArticle::getCampaign)
                .filter(Objects::nonNull)
                .filter(campaign -> campaign.getStatus() != CampaignStatus.FINISHED) // Исключаем завершенные кампании
                .map(c -> CampaignDto.builder()
                        .id(c.getAdvertId())
                        .name(c.getName())
                        .type(c.getType() != null ? c.getType().getDescription() : null)
                        .status(c.getStatus() != null ? c.getStatus().getCode() : null)
                        .statusName(c.getStatus() != null ? c.getStatus().getDescription() : null)
                        .createdAt(c.getCreateTime())
                        .build())
                .collect(Collectors.toList());
    }

    /**
     * Получает остатки товара на складах.
     */
    private List<StockDto> getStocks(Long nmId) {
        // Получаем все остатки для артикула
        List<ru.oparin.solution.model.ProductStock> stocks = stockRepository.findByNmId(nmId);
        
        if (stocks.isEmpty()) {
            return Collections.emptyList();
        }
        
        // Группируем по складам и суммируем количество
        Map<Long, StockAggregate> stockByWarehouse = stocks.stream()
                .collect(Collectors.groupingBy(
                        ru.oparin.solution.model.ProductStock::getWarehouseId,
                        Collectors.collectingAndThen(
                                Collectors.toList(),
                                stockList -> {
                                    int totalAmount = stockList.stream()
                                            .mapToInt(ru.oparin.solution.model.ProductStock::getAmount)
                                            .sum();
                                    java.time.LocalDateTime latestUpdate = stockList.stream()
                                            .map(ru.oparin.solution.model.ProductStock::getUpdatedAt)
                                            .max(java.time.LocalDateTime::compareTo)
                                            .orElse(null);
                                    return new StockAggregate(totalAmount, latestUpdate);
                                }
                        )
                ));
        
        // Получаем названия складов
        Map<Long, String> warehouseNames = warehouseRepository.findAll().stream()
                .collect(Collectors.toMap(
                        w -> Long.valueOf(w.getId()),
                        ru.oparin.solution.model.WbWarehouse::getName,
                        (existing, replacement) -> existing
                ));
        
        // Формируем список DTO
        return stockByWarehouse.entrySet().stream()
                .map(entry -> {
                    Long warehouseId = entry.getKey();
                    StockAggregate aggregate = entry.getValue();
                    String warehouseName = warehouseNames.getOrDefault(warehouseId, "Склад " + warehouseId);
                    
                    return StockDto.builder()
                            .warehouseName(warehouseName)
                            .amount(aggregate.totalAmount)
                            .updatedAt(aggregate.latestUpdate)
                            .build();
                })
                .sorted((a, b) -> b.getAmount().compareTo(a.getAmount())) // Сортируем по убыванию количества
                .collect(Collectors.toList());
    }
    
    /**
     * Вспомогательный класс для агрегации остатков по складу.
     */
    private static class StockAggregate {
        final int totalAmount;
        final java.time.LocalDateTime latestUpdate;
        
        StockAggregate(int totalAmount, java.time.LocalDateTime latestUpdate) {
            this.totalAmount = totalAmount;
            this.latestUpdate = latestUpdate;
        }
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
                .photoTm(card.getPhotoTm())
                .build();
    }

    private ArticleDetailDto mapToArticleDetail(ProductCard card) {
        return ArticleDetailDto.builder()
                .nmId(card.getNmId())
                .imtId(card.getImtId())
                .title(card.getTitle())
                .brand(card.getBrand())
                .subjectName(card.getSubjectName())
                .vendorCode(card.getVendorCode())
                .photoTm(card.getPhotoTm())
                .rating(null) // TODO: получить из API или БД
                .reviewsCount(null) // TODO: получить из API или БД
                .productUrl("https://www.wildberries.ru/catalog/" + card.getNmId() + "/detail.aspx")
                .createdAt(card.getCreatedAt())
                .updatedAt(card.getUpdatedAt())
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
