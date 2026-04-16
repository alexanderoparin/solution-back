package ru.oparin.solution.service;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.oparin.solution.dto.analytics.*;
import ru.oparin.solution.exception.UserException;
import ru.oparin.solution.model.*;
import ru.oparin.solution.repository.*;
import ru.oparin.solution.service.analytics.*;
import ru.oparin.solution.util.PeriodGenerator;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
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
    private final ProductBarcodeRepository barcodeRepository;
    private final WbWarehouseRepository warehouseRepository;
    private final FunnelMetricsCalculator funnelMetricsCalculator;
    private final AdvertisingMetricsCalculator advertisingMetricsCalculator;
    private final MetricValueCalculator metricValueCalculator;
    private final CampaignStatisticsAggregator campaignStatisticsAggregator;
    private final PromotionParticipationRepository promotionParticipationRepository;
    private final CabinetService cabinetService;
    private final ArticleAdCampaignGoalService articleAdCampaignGoalService;


    /**
     * Получает сводную аналитику для продавца (при cabinetId != null — только по выбранному кабинету).
     * При указании page и size возвращается только страница артикулов и totalArticles; aggregatedMetrics не заполняются.
     */
    @Transactional(readOnly = true)
    public SummaryResponseDto getSummary(
            User seller,
            Long cabinetId,
            List<PeriodDto> periods,
            List<Long> excludedNmIds,
            Integer page,
            Integer size,
            String search,
            List<Long> includedNmIds,
            Boolean filterToNone,
            Boolean onlyWithPhoto,
            Boolean onlyPriority
    ) {
        validatePeriods(periods);
        List<PeriodDto> sortedPeriods = sortPeriodsByDateFrom(periods);

        List<ProductCard> visibleCards = getVisibleCards(seller.getId(), cabinetId, excludedNmIds);
        if (Boolean.TRUE.equals(onlyWithPhoto)) {
            visibleCards = visibleCards.stream()
                    .filter(this::cardHasAnyPhoto)
                    .collect(Collectors.toList());
        }
        if (Boolean.TRUE.equals(onlyPriority)) {
            visibleCards = visibleCards.stream()
                    .filter(c -> Boolean.TRUE.equals(c.getIsPriority()))
                    .collect(Collectors.toList());
        }

        boolean paginated = page != null && size != null && size > 0;
        if (paginated) {
            List<ProductCard> filtered = visibleCards;
            if (Boolean.TRUE.equals(filterToNone)) {
                filtered = List.of();
            } else if (includedNmIds != null && !includedNmIds.isEmpty()) {
                var idSet = new java.util.HashSet<>(includedNmIds);
                filtered = filtered.stream()
                        .filter(c -> c.getNmId() != null && idSet.contains(c.getNmId()))
                        .collect(Collectors.toList());
            }
            if (search != null && !search.isBlank()) {
                filtered = filterCardsBySearch(filtered, search.trim());
            }
            int total = filtered.size();
            int from = Math.min(page * size, total);
            int to = Math.min(from + size, total);
            List<ProductCard> pageCards = from < to ? filtered.subList(from, to) : List.of();
            return SummaryResponseDto.builder()
                    .periods(sortedPeriods)
                    .articles(mapToArticleSummaries(pageCards))
                    .aggregatedMetrics(null)
                    .totalArticles((long) total)
                    .build();
        }

        Map<Integer, AggregatedMetricsDto> aggregatedMetrics = calculateAggregatedMetrics(
                visibleCards, sortedPeriods, seller.getId(), cabinetId);
        return SummaryResponseDto.builder()
                .periods(sortedPeriods)
                .articles(mapToArticleSummaries(visibleCards))
                .aggregatedMetrics(aggregatedMetrics)
                .totalArticles(null)
                .build();
    }

    /**
     * Список артикулов кабинета/продавца — только справочная информация для попапа фильтра.
     * Если onlyWithPhoto == true — только артикулы с заполненным фото.
     */
    @Transactional(readOnly = true)
    public List<ArticleSummaryDto> getArticleList(User seller, Long cabinetId, Boolean onlyWithPhoto) {
        List<ProductCard> allCards = getVisibleCards(seller.getId(), cabinetId, null);
        if (Boolean.TRUE.equals(onlyWithPhoto)) {
            allCards = allCards.stream()
                    .filter(this::cardHasAnyPhoto)
                    .collect(Collectors.toList());
        }
        return mapToArticleSummaries(allCards);
    }

    private List<ProductCard> filterCardsBySearch(List<ProductCard> cards, String searchLower) {
        String lower = searchLower.toLowerCase();
        return cards.stream()
                .filter(card ->
                        (card.getTitle() != null && card.getTitle().toLowerCase().contains(lower))
                                || (card.getVendorCode() != null && card.getVendorCode().toLowerCase().contains(lower))
                                || (card.getNmId() != null && String.valueOf(card.getNmId()).contains(lower))
                )
                .collect(Collectors.toList());
    }

    /**
     * Получает детальные метрики по группе (при cabinetId != null — по выбранному кабинету).
     */
    @Transactional(readOnly = true)
    public MetricGroupResponseDto getMetricGroup(
            User seller,
            Long cabinetId,
            String metricName,
            List<PeriodDto> periods,
            List<Long> excludedNmIds,
            Boolean onlyWithPhoto
    ) {
        List<PeriodDto> sortedPeriods = sortPeriodsByDateFrom(periods);
        if (isAdvertisingMetric(metricName)) {
            return getAdvertisingMetricGroup(seller, cabinetId, metricName, sortedPeriods, excludedNmIds, onlyWithPhoto);
        } else {
            return getFunnelMetricGroup(seller, cabinetId, metricName, sortedPeriods, excludedNmIds, onlyWithPhoto);
        }
    }

    private MetricGroupResponseDto getFunnelMetricGroup(
            User seller,
            Long cabinetId,
            String metricName,
            List<PeriodDto> periods,
            List<Long> excludedNmIds,
            Boolean onlyWithPhoto
    ) {
        List<ProductCard> visibleCards = getVisibleCards(seller.getId(), cabinetId, excludedNmIds);
        if (Boolean.TRUE.equals(onlyWithPhoto)) {
            visibleCards = visibleCards.stream()
                    .filter(this::cardHasAnyPhoto)
                    .collect(Collectors.toList());
        }

        List<ArticleMetricDto> articleMetrics = visibleCards.stream()
                .map(card -> calculateArticleMetric(card, metricName, periods, seller.getId(), cabinetId, null))
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
            Long cabinetId,
            String metricName,
            List<PeriodDto> periods,
            List<Long> excludedNmIds,
            Boolean onlyWithPhoto
    ) {
        List<ProductCard> visibleCards = getVisibleCards(seller.getId(), cabinetId, excludedNmIds);
        if (Boolean.TRUE.equals(onlyWithPhoto)) {
            visibleCards = visibleCards.stream()
                    .filter(this::cardHasAnyPhoto)
                    .collect(Collectors.toList());
        }
        List<Long> campaignIds = getCampaignIdsForCabinet(seller.getId(), cabinetId);

        Map<PeriodDto, Map<Long, CampaignStatisticsAggregator.AdvertisingStats>> statsByPeriodByArticle = new HashMap<>();
        for (PeriodDto period : periods) {
            statsByPeriodByArticle.put(period, campaignStatisticsAggregator.aggregateStatsByArticle(campaignIds, period));
        }
        Map<PeriodDto, Map<Long, BigDecimal>> funnelOrdersAmountByPeriodByArticle =
                MetricNames.DRR.equals(metricName)
                        ? preloadFunnelOrdersAmountByArticle(visibleCards, cabinetId, periods)
                        : Collections.emptyMap();

        List<ArticleMetricDto> articleMetrics = new ArrayList<>();
        CampaignStatisticsAggregator.AdvertisingStats emptyStats = new CampaignStatisticsAggregator.AdvertisingStats(
                0, 0, BigDecimal.ZERO, 0, BigDecimal.ZERO);

        for (ProductCard card : visibleCards) {
            Long nmId = card.getNmId();
            if (nmId == null) {
                continue;
            }

            List<PeriodMetricValueDto> periodValues = new ArrayList<>();
            for (PeriodDto period : periods) {
                CampaignStatisticsAggregator.AdvertisingStats stats = statsByPeriodByArticle
                        .getOrDefault(period, Collections.emptyMap())
                        .getOrDefault(nmId, emptyStats);
                Object value = calculateAdvertisingMetricValue(
                        metricName,
                        stats,
                        period,
                        nmId,
                        funnelOrdersAmountByPeriodByArticle
                );
                BigDecimal changePercent = calculateArticleAdvertisingChangePercent(
                        nmId,
                        metricName,
                        period,
                        periods,
                        statsByPeriodByArticle,
                        funnelOrdersAmountByPeriodByArticle
                );

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
                .category(getMetricCategory(metricName))
                .articles(articleMetrics)
                .campaigns(Collections.emptyList())
                .build();
    }

    private List<Long> getCampaignIdsForCabinet(Long sellerId, Long cabinetId) {
        List<PromotionCampaign> campaigns = cabinetId != null
                ? campaignRepository.findByCabinet_Id(cabinetId)
                : campaignRepository.findBySellerId(sellerId);
        return campaigns.stream().map(PromotionCampaign::getAdvertId).collect(Collectors.toList());
    }

    private BigDecimal calculateArticleAdvertisingChangePercent(
            Long nmId,
            String metricName,
            PeriodDto period,
            List<PeriodDto> allPeriodsSortedByDate,
            Map<PeriodDto, Map<Long, CampaignStatisticsAggregator.AdvertisingStats>> statsByPeriodByArticle,
            Map<PeriodDto, Map<Long, BigDecimal>> funnelOrdersAmountByPeriodByArticle
    ) {
        PeriodDto previousPeriod = findPreviousPeriodByDateOrder(period, allPeriodsSortedByDate);
        if (previousPeriod == null) {
            return null;
        }
        CampaignStatisticsAggregator.AdvertisingStats currentStats = statsByPeriodByArticle
                .getOrDefault(period, Collections.emptyMap())
                .getOrDefault(nmId, new CampaignStatisticsAggregator.AdvertisingStats(0, 0, BigDecimal.ZERO, 0, BigDecimal.ZERO));
        CampaignStatisticsAggregator.AdvertisingStats previousStats = statsByPeriodByArticle
                .getOrDefault(previousPeriod, Collections.emptyMap())
                .getOrDefault(nmId, new CampaignStatisticsAggregator.AdvertisingStats(0, 0, BigDecimal.ZERO, 0, BigDecimal.ZERO));
        Object currentValue = calculateAdvertisingMetricValue(
                metricName,
                currentStats,
                period,
                nmId,
                funnelOrdersAmountByPeriodByArticle
        );
        Object previousValue = calculateAdvertisingMetricValue(
                metricName,
                previousStats,
                previousPeriod,
                nmId,
                funnelOrdersAmountByPeriodByArticle
        );
        if (MetricNames.isPercentageMetric(metricName)) {
            return calculatePercentageDifference(currentValue, previousValue);
        } else {
            return calculatePercentageChange(currentValue, previousValue);
        }
    }

    private Object calculateAdvertisingMetricValue(
            String metricName,
            CampaignStatisticsAggregator.AdvertisingStats stats,
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

    private Map<PeriodDto, Map<Long, BigDecimal>> preloadFunnelOrdersAmountByArticle(
            List<ProductCard> cards,
            Long cabinetId,
            List<PeriodDto> periods
    ) {
        List<Long> nmIds = cards.stream()
                .map(ProductCard::getNmId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (nmIds.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<PeriodDto, Map<Long, BigDecimal>> result = new HashMap<>();
        for (PeriodDto period : periods) {
            List<ProductCardAnalytics> analytics = cabinetId != null
                    ? analyticsRepository.findByCabinet_IdAndProductCardNmIdInAndDateBetween(
                    cabinetId,
                    nmIds,
                    period.getDateFrom(),
                    period.getDateTo()
            )
                    : analyticsRepository.findByProductCardNmIdInAndDateBetween(
                    nmIds,
                    period.getDateFrom(),
                    period.getDateTo()
            );
            Map<Long, BigDecimal> ordersAmountByNmId = new HashMap<>();
            for (ProductCardAnalytics item : analytics) {
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

    private Object calculateAdvertisingMetricValue(String metricName, CampaignStatisticsAggregator.AdvertisingStats stats) {
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

    /** allPeriods должен быть отсортирован по dateFrom (слева направо: старый → новый). */
    private BigDecimal calculateCampaignChangePercent(
            Long campaignId,
            String metricName,
            PeriodDto period,
            List<PeriodDto> allPeriodsSortedByDate
    ) {
        PeriodDto previousPeriod = findPreviousPeriodByDateOrder(period, allPeriodsSortedByDate);
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
            Long cabinetId,
            List<PeriodDto> periods
    ) {
        Map<PeriodDto, CampaignStatisticsAggregator.AdvertisingStats> cache = new HashMap<>();
        List<PromotionCampaign> campaigns = cabinetId != null
                ? campaignRepository.findByCabinet_Id(cabinetId)
                : campaignRepository.findBySellerId(sellerId);
        List<Long> campaignIds = campaigns.stream()
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
     * @param campaignDateFrom начало периода для метрик РК (опционально)
     * @param campaignDateTo конец периода для метрик РК (опционально)
     */
    @Transactional(readOnly = true)
    public ArticleResponseDto getArticle(User seller, Long cabinetId, Long nmId, List<PeriodDto> periods,
                                         LocalDate campaignDateFrom, LocalDate campaignDateTo) {
        ProductCard card = findCardBySeller(nmId, seller.getId(), cabinetId);
        Long cardCabinetId = card.getCabinet() != null ? card.getCabinet().getId() : null;

        List<DailyDataDto> dailyData = getDailyData(nmId, cardCabinetId);
        List<PromotionParticipation> participations = cardCabinetId != null
                ? promotionParticipationRepository.findByCabinet_IdAndNmId(cardCabinetId, nmId)
                : Collections.emptyList();
        List<String> wbPromotionNames = participations.stream()
                .map(PromotionParticipation::getWbPromotionName)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        List<String> wbPromotionTypes = participations.stream()
                .map(PromotionParticipation::getWbPromotionType)
                .map(t -> t != null ? t : "")
                .collect(Collectors.toList());
        Boolean inWbPromotion = !wbPromotionNames.isEmpty();
        List<ArticleSummaryDto> bundleProducts = getBundleProducts(card, cardCabinetId);
        LocalDateTime lastStocksUpdateTriggeredAt = cardCabinetId != null
                ? cabinetService.findById(cardCabinetId).map(Cabinet::getLastStocksUpdateRequestedAt).orElse(null)
                : null;
        String adCampaignGoal = cardCabinetId != null
                ? articleAdCampaignGoalService.findGoalText(cardCabinetId, nmId).orElse(null)
                : null;

        return ArticleResponseDto.builder()
                .article(mapToArticleDetail(card))
                .periods(periods)
                .metrics(calculateAllMetrics(card, periods, seller.getId(), cardCabinetId))
                .dailyData(dailyData)
                .campaigns(getCampaigns(nmId, cardCabinetId, campaignDateFrom, campaignDateTo))
                .inWbPromotion(inWbPromotion)
                .wbPromotionNames(wbPromotionNames)
                .wbPromotionTypes(wbPromotionTypes)
                .stocks(getStocks(nmId, cardCabinetId))
                .bundleProducts(bundleProducts)
                .lastStocksUpdateTriggeredAt(lastStocksUpdateTriggeredAt)
                .adCampaignGoal(adCampaignGoal)
                .build();
    }

    /**
     * Товары «в связке» — другие артикулы с тем же IMT ID в карточке (тот же кабинет/продавец), без текущего nmId.
     */
    private List<ArticleSummaryDto> getBundleProducts(ProductCard card, Long cardCabinetId) {
        if (card.getImtId() == null) {
            return java.util.Collections.emptyList();
        }
        List<ProductCard> sameImt = cardCabinetId != null
                ? productCardRepository.findByImtIdAndCabinet_Id(card.getImtId(), cardCabinetId)
                : productCardRepository.findByImtIdAndSeller_Id(card.getImtId(), card.getSeller().getId());
        return sameImt.stream()
                .filter(c -> !c.getNmId().equals(card.getNmId()))
                .map(this::mapToArticleSummary)
                .collect(Collectors.toList());
    }

    /**
     * Определяет, участвует ли товар в акции WB по скидке продавца (не путать с рекламными кампаниями).
     * Берётся последняя известная дата с данными о цене: если скидка продавца > 0 — товар в акции.
     */
    private Boolean computeInWbPromotion(List<DailyDataDto> dailyData) {
        if (dailyData == null || dailyData.isEmpty()) {
            return null;
        }
        return dailyData.stream()
                .filter(d -> d.getSellerDiscount() != null)
                .max(java.util.Comparator.comparing(DailyDataDto::getDate))
                .map(d -> d.getSellerDiscount() > 0)
                .orElse(null);
    }

    private void validatePeriods(List<PeriodDto> periods) {
        if (!PeriodGenerator.validatePeriods(periods)) {
            throw new IllegalArgumentException("Периоды некорректны: дата начала периода не может быть позже даты окончания");
        }
    }

    private List<ProductCard> getVisibleCards(Long sellerId, Long cabinetId, List<Long> excludedNmIds) {
        List<ProductCard> allCards = cabinetId != null
                ? productCardRepository.findByCabinet_Id(cabinetId)
                : productCardRepository.findBySellerId(sellerId);
        return ProductCardFilter.filterVisibleCards(allCards, excludedNmIds).stream()
                .sorted(Comparator.comparing(ProductCard::getNmId, Comparator.nullsLast(Comparator.naturalOrder())))
                .collect(Collectors.toList());
    }

    private Map<Integer, AggregatedMetricsDto> calculateAggregatedMetrics(
            List<ProductCard> cards,
            List<PeriodDto> periods,
            Long sellerId,
            Long cabinetId
    ) {
        Map<Integer, AggregatedMetricsDto> result = new HashMap<>();
        Set<Long> nmIdsFilter = cards.stream()
                .map(ProductCard::getNmId)
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
            ProductCard card,
            String metricName,
            List<PeriodDto> periods,
            Long sellerId,
            Long cabinetId,
            Map<PeriodDto, CampaignStatisticsAggregator.AdvertisingStats> advertisingStatsCache
    ) {
        List<PeriodMetricValueDto> periodValues = periods.stream()
                .map(period -> calculatePeriodMetricValue(card, metricName, period, periods, sellerId, cabinetId, advertisingStatsCache))
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
            Long cabinetId,
            Map<PeriodDto, CampaignStatisticsAggregator.AdvertisingStats> advertisingStatsCache
    ) {
        Object value = metricValueCalculator.calculateValue(card, metricName, period, sellerId, cabinetId, advertisingStatsCache);
        BigDecimal changePercent = calculateChangePercent(card, metricName, period, allPeriods, sellerId, cabinetId, value, advertisingStatsCache);

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
            List<PeriodDto> allPeriodsSortedByDate,
            Long sellerId,
            Long cabinetId,
            Object currentValue,
            Map<PeriodDto, CampaignStatisticsAggregator.AdvertisingStats> advertisingStatsCache
    ) {
        if (currentValue == null) {
            return null;
        }
        PeriodDto previousPeriod = findPreviousPeriodByDateOrder(period, allPeriodsSortedByDate);
        if (previousPeriod == null) {
            return null;
        }

        Object previousValue = metricValueCalculator.calculateValue(card, metricName, previousPeriod, sellerId, cabinetId, advertisingStatsCache);
        
        // Для процентных метрик вычисляем разницу, для остальных - процентное изменение
        if (MetricNames.isPercentageMetric(metricName)) {
            return calculatePercentageDifference(currentValue, previousValue);
        } else {
            return calculatePercentageChange(currentValue, previousValue);
        }
    }

    /** Список периодов должен быть отсортирован по dateFrom (слева направо: старый → новый). */
    private List<PeriodDto> sortPeriodsByDateFrom(List<PeriodDto> periods) {
        return periods.stream()
                .sorted(Comparator.comparing(PeriodDto::getDateFrom))
                .collect(Collectors.toList());
    }

    /** Предыдущий период по хронологическому порядку (слева в таблице). allPeriods должен быть отсортирован по dateFrom. */
    private PeriodDto findPreviousPeriodByDateOrder(PeriodDto currentPeriod, List<PeriodDto> allPeriodsSortedByDate) {
        int idx = -1;
        for (int i = 0; i < allPeriodsSortedByDate.size(); i++) {
            if (Objects.equals(allPeriodsSortedByDate.get(i).getId(), currentPeriod.getId())) {
                idx = i;
                break;
            }
        }
        if (idx <= 0) {
            return null;
        }
        return allPeriodsSortedByDate.get(idx - 1);
    }

    private PeriodDto findPreviousPeriod(PeriodDto currentPeriod, List<PeriodDto> allPeriods) {
        return findPreviousPeriodByDateOrder(currentPeriod, allPeriods);
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

    private List<MetricDto> calculateAllMetrics(ProductCard card, List<PeriodDto> periods, Long sellerId, Long cabinetId) {
        Map<PeriodDto, CampaignStatisticsAggregator.AdvertisingStats> advertisingStatsCache =
                preloadAdvertisingStats(sellerId, cabinetId, periods);

        List<MetricDto> metrics = new ArrayList<>();

        for (String metricName : MetricNames.getAllMetrics()) {
            List<PeriodMetricValueDto> periodValues = periods.stream()
                    .map(period -> {
                        Object value = metricValueCalculator.calculateValue(card, metricName, period, sellerId, cabinetId, advertisingStatsCache);
                        BigDecimal changePercent = calculateChangePercent(
                                card, metricName, period, periods, sellerId, cabinetId, value, advertisingStatsCache);
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

    /**
     * Одна запись цены на день для таблицы аналитики: при нескольких строках (размеры / агрегат)
     * предпочитаем вариант с заполненным СПП, иначе прежняя логика (без размера, затем любая).
     */
    private static ProductPriceHistory pickRepresentativePriceRow(List<ProductPriceHistory> prices) {
        if (prices == null || prices.isEmpty()) {
            throw new IllegalArgumentException("prices must be non-empty");
        }
        Optional<ProductPriceHistory> consolidatedWithSpp = prices.stream()
                .filter(p -> p.getSizeId() == null && p.getSppDiscount() != null)
                .findFirst();
        if (consolidatedWithSpp.isPresent()) {
            return consolidatedWithSpp.get();
        }
        Optional<ProductPriceHistory> anyWithSpp = prices.stream()
                .filter(p -> p.getSppDiscount() != null)
                .findFirst();
        if (anyWithSpp.isPresent()) {
            return anyWithSpp.get();
        }
        Optional<ProductPriceHistory> withoutSize = prices.stream()
                .filter(p -> p.getSizeId() == null)
                .findFirst();
        return withoutSize.orElseGet(() -> prices.get(0));
    }

    private List<DailyDataDto> getDailyData(Long nmId, Long cabinetId) {
        LocalDate endDate = LocalDate.now().minusDays(1);
        LocalDate startDate = endDate.minusDays(13);

        List<ProductCardAnalytics> funnelData = cabinetId != null
                ? analyticsRepository.findByCabinet_IdAndProductCardNmIdAndDateBetween(cabinetId, nmId, startDate, endDate)
                : analyticsRepository.findByProductCardNmIdAndDateBetween(nmId, startDate, endDate);

        List<PromotionCampaignStatistics> advertisingData = campaignStatisticsRepository.findByNmIdAndDateBetween(nmId, startDate, endDate);

        List<ProductPriceHistory> priceData = cabinetId != null
                ? priceHistoryRepository.findByNmIdAndDateBetweenAndCabinet_Id(nmId, startDate, endDate, cabinetId)
                : priceHistoryRepository.findByNmIdAndDateBetween(nmId, startDate, endDate);
        
        // Группируем рекламные данные по датам
        Map<LocalDate, AdvertisingDailyStats> advertisingByDate = advertisingData.stream()
                .collect(Collectors.groupingBy(
                        PromotionCampaignStatistics::getDate,
                        Collectors.collectingAndThen(
                                Collectors.toList(),
                                stats -> aggregateAdvertisingStats(stats)
                        )
                ));
        
        // Группируем данные ценообразования по датам (одна строка на день для таблицы)
        Map<LocalDate, ProductPriceHistory> priceByDate = priceData.stream()
                .collect(Collectors.groupingBy(
                        ProductPriceHistory::getDate,
                        Collectors.collectingAndThen(
                                Collectors.toList(),
                                AnalyticsService::pickRepresentativePriceRow
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
                                .ctr(advertising.ctr);
                        // СРО и ДРР считаем по тем же «Заказали», что и в таблице (воронка), чтобы строка была консистентна
                        if (funnel != null && funnel.getOrders() != null && funnel.getOrders() > 0 && advertising.costs != null) {
                            builder.cpo(MathUtils.divideSafely(advertising.costs, BigDecimal.valueOf(funnel.getOrders())));
                        } else {
                            builder.cpo(advertising.cpo);
                        }
                        if (funnel != null && funnel.getOrdersSum() != null && funnel.getOrdersSum().compareTo(BigDecimal.ZERO) > 0 && advertising.costs != null) {
                            builder.drr(MathUtils.calculatePercentage(advertising.costs, funnel.getOrdersSum()));
                        } else {
                            builder.drr(advertising.drr);
                        }
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
                        if (price.getSppDiscount() != null && price.getClubDiscountedPrice() != null) {
                            // СПП (%) берем напрямую из БД
                            BigDecimal sppPercent = BigDecimal.valueOf(price.getSppDiscount());
                            builder.sppPercent(sppPercent);
                            
                            // СПП (руб) = (Цена со скидкой WB Клуба * СПП %) / 100
                            BigDecimal sppAmount = price.getClubDiscountedPrice()
                                    .multiply(sppPercent)
                                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
                            builder.sppAmount(sppAmount);
                            
                            // Цена с СПП = Цена со скидкой WB Клуба - СПП (руб)
                            BigDecimal priceWithSpp = price.getClubDiscountedPrice().subtract(sppAmount);
                            builder.priceWithSpp(priceWithSpp);
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

    private List<CampaignDto> getCampaigns(Long nmId, Long cabinetId, LocalDate campaignDateFrom, LocalDate campaignDateTo) {
        List<CampaignArticle> campaignArticles = campaignArticleRepository.findByNmId(nmId);

        List<PromotionCampaign> campaigns = campaignArticles.stream()
                .map(CampaignArticle::getCampaign)
                .filter(Objects::nonNull)
                .filter(campaign -> campaign.getStatus() != CampaignStatus.FINISHED)
                .filter(campaign -> cabinetId == null || (campaign.getCabinet() != null && campaign.getCabinet().getId().equals(cabinetId)))
                .distinct()
                .collect(Collectors.toList());

        if (campaigns.isEmpty()) {
            return Collections.emptyList();
        }

        boolean withMetrics = campaignDateFrom != null && campaignDateTo != null && !campaignDateFrom.isAfter(campaignDateTo);
        Map<Long, List<PromotionCampaignStatistics>> statsByCampaign = new HashMap<>();
        if (withMetrics) {
            List<Long> campaignIds = campaigns.stream().map(PromotionCampaign::getAdvertId).collect(Collectors.toList());
            List<PromotionCampaignStatistics> allStats = campaignStatisticsRepository.findByCampaignAdvertIdInAndDateBetween(
                    campaignIds, campaignDateFrom, campaignDateTo);
            statsByCampaign = allStats.stream().collect(Collectors.groupingBy(s -> s.getCampaign().getAdvertId()));
        }

        final Map<Long, List<PromotionCampaignStatistics>> statsByCampaignFinal = statsByCampaign;
        return campaigns.stream()
                .map(c -> {
                    CampaignDto.CampaignDtoBuilder b = CampaignDto.builder()
                            .id(c.getAdvertId())
                            .name(c.getName())
                            .type(c.getType() != null ? c.getType().getDescription() : null)
                            .status(c.getStatus() != null ? c.getStatus().getCode() : null)
                            .statusName(c.getStatus() != null ? c.getStatus().getDescription() : null)
                            .createdAt(c.getCreateTime())
                            .updatedAt(resolveCampaignUpdatedAt(c));
                    if (withMetrics) {
                        List<PromotionCampaignStatistics> stats = statsByCampaignFinal.getOrDefault(c.getAdvertId(), Collections.emptyList());
                        int views = 0, clicks = 0, orders = 0, cart = 0;
                        BigDecimal sum = BigDecimal.ZERO;
                        for (PromotionCampaignStatistics s : stats) {
                            if (s.getViews() != null) views += s.getViews();
                            if (s.getClicks() != null) clicks += s.getClicks();
                            if (s.getSum() != null) sum = sum.add(s.getSum());
                            if (s.getOrders() != null) orders += s.getOrders();
                            if (s.getAtbs() != null) cart += s.getAtbs();
                        }
                        BigDecimal ctr = MathUtils.calculatePercentage(clicks, views);
                        BigDecimal cpc = (clicks > 0 && sum != null) ? sum.divide(BigDecimal.valueOf(clicks), 2, RoundingMode.HALF_UP) : null;
                        b.views(views > 0 ? views : null)
                                .clicks(clicks > 0 ? clicks : null)
                                .ctr(ctr)
                                .cpc(cpc)
                                .costs(sum != null && sum.compareTo(BigDecimal.ZERO) > 0 ? sum : null)
                                .cart(cart > 0 ? cart : null)
                                .orders(orders > 0 ? orders : null);
                    }
                    return b.build();
                })
                .collect(Collectors.toList());
    }

    /**
     * Получает остатки товара на складах.
     */
    private List<StockDto> getStocks(Long nmId, Long cabinetId) {
        List<ProductStock> stocks = cabinetId != null
                ? stockRepository.findByNmIdAndCabinet_Id(nmId, cabinetId)
                : stockRepository.findByNmId(nmId);
        
        if (stocks.isEmpty()) {
            return Collections.emptyList();
        }
        
        // Группируем по складам и суммируем количество
        Map<Long, StockAggregate> stockByWarehouse = stocks.stream()
                .collect(Collectors.groupingBy(
                        ProductStock::getWarehouseId,
                        Collectors.collectingAndThen(
                                Collectors.toList(),
                                stockList -> {
                                    int totalAmount = stockList.stream()
                                            .mapToInt(ProductStock::getAmount)
                                            .sum();
                                    LocalDateTime latestUpdate = stockList.stream()
                                            .map(ProductStock::getUpdatedAt)
                                            .max(LocalDateTime::compareTo)
                                            .orElse(null);
                                    return new StockAggregate(totalAmount, latestUpdate);
                                }
                        )
                ));
        
        // Получаем названия складов
        Map<Long, String> warehouseNames = warehouseRepository.findAll().stream()
                .collect(Collectors.toMap(
                        w -> Long.valueOf(w.getId()),
                        WbWarehouse::getName,
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
                            .amount(aggregate.getTotalAmount())
                            .updatedAt(aggregate.getLatestUpdate())
                            .build();
                })
                .sorted((a, b) -> b.getAmount().compareTo(a.getAmount())) // Сортируем по убыванию количества
                .collect(Collectors.toList());
    }

    private static final int DEFAULT_CAMPAIGNS_PERIOD_DAYS = 14;

    private static LocalDateTime resolveCampaignUpdatedAt(PromotionCampaign c) {
        if (c.getChangeTime() != null) {
            return c.getChangeTime();
        }
        return c.getUpdatedAt();
    }

    /**
     * Список рекламных кампаний кабинета с агрегированной статистикой за период.
     * Если dateFrom/dateTo не заданы — используются последние 14 дней.
     */
    @Transactional(readOnly = true)
    public List<CampaignDto> listCampaignsByCabinet(Long cabinetId, LocalDate dateFrom, LocalDate dateTo) {
        if (cabinetId == null) {
            return Collections.emptyList();
        }
        LocalDate to = dateTo != null ? dateTo : LocalDate.now();
        LocalDate from = dateFrom != null ? dateFrom : to.minusDays(DEFAULT_CAMPAIGNS_PERIOD_DAYS - 1);
        if (from.isAfter(to)) {
            from = to.minusDays(DEFAULT_CAMPAIGNS_PERIOD_DAYS - 1);
        }

        List<PromotionCampaign> campaigns = campaignRepository.findByCabinet_Id(cabinetId).stream()
                .filter(c -> c.getStatus() != CampaignStatus.FINISHED)
                .collect(Collectors.toList());
        if (campaigns.isEmpty()) {
            return Collections.emptyList();
        }
        List<Long> campaignIds = campaigns.stream().map(PromotionCampaign::getAdvertId).collect(Collectors.toList());
        List<PromotionCampaignStatistics> allStats = campaignStatisticsRepository.findByCampaignAdvertIdInAndDateBetween(
                campaignIds, from, to);
        Map<Long, List<PromotionCampaignStatistics>> statsByCampaign = allStats.stream()
                .collect(Collectors.groupingBy(s -> s.getCampaign().getAdvertId()));

        List<Object[]> articleCounts = campaignArticleRepository.countByCampaignIdIn(campaignIds);
        Map<Long, Integer> articlesCountByCampaign = articleCounts.stream()
                .collect(Collectors.toMap(row -> (Long) row[0], row -> ((Number) row[1]).intValue()));

        return campaigns.stream()
                .map(c -> {
                    List<PromotionCampaignStatistics> stats = statsByCampaign.getOrDefault(c.getAdvertId(), Collections.emptyList());
                    int views = 0, clicks = 0, orders = 0, cart = 0;
                    BigDecimal sum = BigDecimal.ZERO;
                    for (PromotionCampaignStatistics s : stats) {
                        if (s.getViews() != null) views += s.getViews();
                        if (s.getClicks() != null) clicks += s.getClicks();
                        if (s.getSum() != null) sum = sum.add(s.getSum());
                        if (s.getOrders() != null) orders += s.getOrders();
                        if (s.getAtbs() != null) cart += s.getAtbs();
                    }
                    BigDecimal ctr = MathUtils.calculatePercentage(clicks, views);
                    BigDecimal cpc = (clicks > 0 && sum != null) ? sum.divide(BigDecimal.valueOf(clicks), 2, RoundingMode.HALF_UP) : null;
                    Integer articlesCount = articlesCountByCampaign.getOrDefault(c.getAdvertId(), 0);
                    return CampaignDto.builder()
                            .id(c.getAdvertId())
                            .name(c.getName())
                            .type(c.getType() != null ? c.getType().getDescription() : null)
                            .status(c.getStatus() != null ? c.getStatus().getCode() : null)
                            .statusName(c.getStatus() != null ? c.getStatus().getDescription() : null)
                            .createdAt(c.getCreateTime())
                            .updatedAt(resolveCampaignUpdatedAt(c))
                            .articlesCount(articlesCount)
                            .views(views > 0 ? views : null)
                            .clicks(clicks > 0 ? clicks : null)
                            .ctr(ctr)
                            .cpc(cpc)
                            .costs(sum != null && sum.compareTo(BigDecimal.ZERO) > 0 ? sum : null)
                            .cart(cart > 0 ? cart : null)
                            .orders(orders > 0 ? orders : null)
                            .build();
                })
                .collect(Collectors.toList());
    }

    /**
     * Детали рекламной кампании (комбо): название, статус, список артикулов с фото и названием.
     * Сначала поиск по кабинету; если не найдено — по продавцу (кампания может быть в другом кабинете того же продавца).
     */
    @Transactional(readOnly = true)
    public CampaignDetailDto getCampaignDetail(Long campaignId, Long cabinetId, Long sellerId) {
        PromotionCampaign campaign = null;
        Long cabinetIdForArticles = cabinetId;

        if (cabinetId != null) {
            campaign = campaignRepository.findByAdvertIdAndCabinet_Id(campaignId, cabinetId).orElse(null);
        }
        if (campaign == null && sellerId != null) {
            campaign = campaignRepository.findByAdvertIdAndSeller_Id(campaignId, sellerId).orElse(null);
            if (campaign != null) {
                cabinetIdForArticles = campaign.getCabinet() != null ? campaign.getCabinet().getId() : cabinetId;
            }
        }
        if (campaign == null) {
            return null;
        }
        if (cabinetIdForArticles == null) {
            return null;
        }
        final Long finalCabinetId = cabinetIdForArticles;
        List<CampaignArticle> campaignArticles = campaignArticleRepository.findByCampaignId(campaign.getAdvertId());
        List<Long> nmIds = campaignArticles.stream()
                .map(CampaignArticle::getNmId)
                .distinct()
                .collect(Collectors.toList());
        List<ArticleSummaryDto> articles = nmIds.stream()
                .map(nmId -> productCardRepository.findByNmIdAndCabinet_Id(nmId, finalCabinetId)
                        .map(this::mapToArticleSummary)
                        .orElseGet(() -> ArticleSummaryDto.builder()
                                .nmId(nmId)
                                .title("Артикул " + nmId)
                                .photoTm(null)
                                .build()))
                .collect(Collectors.toList());
        return CampaignDetailDto.builder()
                .id(campaign.getAdvertId())
                .name(campaign.getName())
                .status(campaign.getStatus() != null ? campaign.getStatus().getCode() : null)
                .statusName(campaign.getStatus() != null ? campaign.getStatus().getDescription() : null)
                .articlesCount(articles.size())
                .articles(articles)
                .createdAt(campaign.getCreateTime())
                .build();
    }

    /**
     * Получает детализацию остатков по размерам для товара на конкретном складе.
     *
     * @param nmId артикул товара
     * @param warehouseName название склада
     * @return список остатков по размерам
     */
    public List<StockSizeDto> getStockSizes(Long nmId, String warehouseName) {
        // Находим ID склада по названию
        Long warehouseId = warehouseRepository.findAll().stream()
                .filter(w -> w.getName().equals(warehouseName))
                .map(w -> Long.valueOf(w.getId()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Склад не найден: " + warehouseName));
        
        // Получаем все остатки для товара на этом складе
        List<ProductStock> stocks = stockRepository.findByNmIdAndWarehouseId(nmId, warehouseId);

        // Получаем информацию о баркодах для этого товара (все размеры)
        Map<String, ProductBarcode> barcodeMap = barcodeRepository.findByNmId(nmId).stream()
                .collect(Collectors.toMap(
                        ProductBarcode::getBarcode,
                        b -> b,
                        (existing, replacement) -> existing
                ));
        
        // Все уникальные размеры из баркодов (для вывода и нулевых остатков)
        Map<String, StockSizeAggregate> allSizes = new HashMap<>();
        for (ProductBarcode barcode : barcodeMap.values()) {
            String sizeKey = barcode.getWbSize() != null && !barcode.getWbSize().isEmpty()
                    ? barcode.getWbSize()
                    : (barcode.getTechSize() != null ? barcode.getTechSize() : "Неизвестно");
            if (!allSizes.containsKey(sizeKey)) {
                allSizes.put(sizeKey, new StockSizeAggregate(
                        barcode.getTechSize(),
                        barcode.getWbSize(),
                        0
                ));
            }
        }

        // Суммируем остатки по размерам из ProductStock
        for (ProductStock stock : stocks) {
            ProductBarcode barcode = barcodeMap.get(stock.getBarcode());
            if (barcode == null) {
                continue;
            }
            String sizeKey = barcode.getWbSize() != null && !barcode.getWbSize().isEmpty()
                    ? barcode.getWbSize()
                    : (barcode.getTechSize() != null ? barcode.getTechSize() : "Неизвестно");
            StockSizeAggregate agg = allSizes.get(sizeKey);
            if (agg != null) {
                agg.setAmount(agg.getAmount() + stock.getAmount());
            } else {
                allSizes.put(sizeKey, new StockSizeAggregate(
                        barcode.getTechSize(),
                        barcode.getWbSize(),
                        stock.getAmount()
                ));
            }
        }

        // Формируем список DTO (все размеры, включая с нулём)
        return allSizes.values().stream()
                .map(agg -> StockSizeDto.builder()
                        .techSize(agg.getTechSize())
                        .wbSize(agg.getWbSize())
                        .amount(agg.getAmount())
                        .build())
                .sorted((a, b) -> {
                    // Сортируем по wbSize (числовому размеру), если есть
                    if (a.getWbSize() != null && b.getWbSize() != null) {
                        try {
                            return Integer.compare(Integer.parseInt(a.getWbSize()), Integer.parseInt(b.getWbSize()));
                        } catch (NumberFormatException e) {
                            return a.getWbSize().compareTo(b.getWbSize());
                        }
                    }
                    // Иначе по techSize
                    String aSize = a.getTechSize() != null ? a.getTechSize() : "";
                    String bSize = b.getTechSize() != null ? b.getTechSize() : "";
                    return aSize.compareTo(bSize);
                })
                .collect(Collectors.toList());
    }
    
    /**
     * Вспомогательный класс для агрегации остатков по складу.
     */
    @Value
    private static class StockAggregate {
        int totalAmount;
        LocalDateTime latestUpdate;
    }

    /**
     * Вспомогательный класс для агрегации остатков по размерам.
     */
    @Getter
    @Setter
    private static class StockSizeAggregate {
        String techSize;
        String wbSize;
        int amount;

        StockSizeAggregate(String techSize, String wbSize, int amount) {
            this.techSize = techSize;
            this.wbSize = wbSize;
            this.amount = amount;
        }
    }

    public ProductCard findCardBySeller(Long nmId, Long sellerId) {
        return findCardBySeller(nmId, sellerId, null);
    }

    public ProductCard findCardBySeller(Long nmId, Long sellerId, Long cabinetId) {
        ProductCard card = (cabinetId != null
                ? productCardRepository.findByNmIdAndCabinet_Id(nmId, cabinetId)
                : productCardRepository.findByNmId(nmId))
                .orElseThrow(() -> new UserException("Артикул не найден: " + nmId, HttpStatus.NOT_FOUND));

        if (!card.getSeller().getId().equals(sellerId)) {
            throw new UserException("Артикул не принадлежит продавцу", HttpStatus.FORBIDDEN);
        }

        return card;
    }

    @Transactional
    public void updateArticlePriority(User seller, Long cabinetId, Long nmId, boolean isPriority) {
        ProductCard card = findCardBySeller(nmId, seller.getId(), cabinetId);
        card.setIsPriority(isPriority);
        productCardRepository.save(card);
    }

    private List<ArticleSummaryDto> mapToArticleSummaries(List<ProductCard> cards) {
        return cards.stream()
                .map(this::mapToArticleSummary)
                .collect(Collectors.toList());
    }

    private boolean cardHasAnyPhoto(ProductCard card) {
        return (card.getPhotoTm() != null && !card.getPhotoTm().isBlank())
                || (card.getPhotoC246x328() != null && !card.getPhotoC246x328().isBlank());
    }

    private ArticleSummaryDto mapToArticleSummary(ProductCard card) {
        return ArticleSummaryDto.builder()
                .nmId(card.getNmId())
                .title(card.getTitle())
                .brand(card.getBrand())
                .subjectName(card.getSubjectName())
                .photoTm(card.getPhotoTm())
                .photoC246x328(card.getPhotoC246x328())
                .vendorCode(card.getVendorCode())
                .rating(card.getRating())
                .reviewsCount(card.getReviewsCount())
                .isPriority(Boolean.TRUE.equals(card.getIsPriority()))
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
                .photoC246x328(card.getPhotoC246x328())
                .rating(card.getRating())
                .reviewsCount(card.getReviewsCount())
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
