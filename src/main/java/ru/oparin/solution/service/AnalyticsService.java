package ru.oparin.solution.service;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.oparin.solution.dto.analytics.*;
import ru.oparin.solution.exception.UserException;
import ru.oparin.solution.model.*;
import ru.oparin.solution.repository.*;
import ru.oparin.solution.service.analytics.*;
import ru.oparin.solution.service.campaign.BidderStatusResolver;
import ru.oparin.solution.service.campaign.WbCampaignGoalService;
import ru.oparin.solution.util.ArticleRatingUtils;
import ru.oparin.solution.util.PeriodGenerator;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Сервис для работы с аналитикой.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AnalyticsService {

    /** Дней в суточной выборке по умолчанию (вчера и ещё 13 дней назад). */
    private static final int DEFAULT_DAILY_DATA_SPAN_DAYS = 14;

    /** Максимум календарных дней в одном запросе {@code dailyData} (защита от слишком тяжёлых выборок). */
    private static final int MAX_DAILY_DATA_SPAN_DAYS = 120;

    private final WbProductCardRepository productCardRepository;
    private final OzonProductCardRepository ozonProductCardRepository;
    private final OzonProductPriceHistoryRepository ozonProductPriceHistoryRepository;
    private final OzonProductStockRepository ozonProductStockRepository;
    private final OzonProductCardAnalyticsRepository ozonProductCardAnalyticsRepository;
    private final CabinetService cabinetService;
    private final WbProductCardAnalyticsRepository analyticsRepository;
    private final WbPromotionCampaignRepository campaignRepository;
    private final OzonPromotionCampaignRepository ozonPromotionCampaignRepository;
    private final OzonPromotionCampaignStatisticsRepository ozonPromotionCampaignStatisticsRepository;
    private final OzonCampaignArticleRepository ozonCampaignArticleRepository;
    private final WbCampaignArticleRepository campaignArticleRepository;
    private final WbPromotionCampaignStatisticsRepository campaignStatisticsRepository;
    private final WbProductPriceHistoryRepository priceHistoryRepository;
    private final WbProductStockRepository stockRepository;
    private final WbProductFbsStockRepository fbsStockRepository;
    private final WbProductBarcodeRepository barcodeRepository;
    private final WbWarehouseRepository warehouseRepository;
    private final WbSellerWarehouseRepository sellerWarehouseRepository;
    private final FunnelMetricsCalculator funnelMetricsCalculator;
    private final AdvertisingMetricsCalculator advertisingMetricsCalculator;
    private final MetricValueCalculator metricValueCalculator;
    private final WbCampaignStatisticsAggregator campaignStatisticsAggregator;
    private final WbPromotionNormQueryStatisticsService normQueryStatisticsService;
    private final WbPromotionParticipationRepository promotionParticipationRepository;
    private final WbArticleGoalService articleGoalService;
    private final WbCampaignGoalService campaignGoalService;
    private final WbCampaignManagementStateRepository campaignManagementStateRepository;
    private final WbCampaignScheduleSlotRepository campaignScheduleSlotRepository;
    private final BidderStatusResolver bidderStatusResolver;


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
            Boolean onlyPriority,
            Boolean onlyInAdvertising,
            String sortBy,
            String sortDir
    ) {
        validatePeriods(periods);
        List<PeriodDto> sortedPeriods = sortPeriodsByDateFrom(periods);

        if (cabinetId != null) {
            Cabinet cabinet = cabinetService.findByIdWithUserOrThrow(cabinetId);
            if (cabinet.getMarketplaceType() == MarketplaceType.OZON) {
                return getOzonSummary(
                        cabinetId,
                        sortedPeriods,
                        excludedNmIds,
                        page,
                        size,
                        search,
                        includedNmIds,
                        filterToNone,
                        onlyWithPhoto,
                        onlyInAdvertising,
                        sortDir
                );
            }
        }

        List<WbProductCard> visibleCards = applyCatalogFilters(
                getVisibleCards(seller.getId(), cabinetId, excludedNmIds),
                seller.getId(),
                cabinetId,
                onlyWithPhoto,
                onlyPriority,
                onlyInAdvertising
        );

        ArticleSummarySortField resolvedSortBy = ArticleSummarySortField.fromParam(sortBy);
        Sort.Direction resolvedSortDir = Sort.Direction.fromOptionalString(sortDir).orElse(Sort.Direction.DESC);

        boolean paginated = page != null && size != null && size > 0;
        if (paginated) {
            List<WbProductCard> filtered = visibleCards;
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
            sortProductCards(filtered, resolvedSortBy, resolvedSortDir);
            int total = filtered.size();
            int from = Math.min(page * size, total);
            int to = Math.min(from + size, total);
            List<WbProductCard> pageCards = from < to ? filtered.subList(from, to) : List.of();
            boolean itemRatingSupported = isItemRatingSupported(seller.getId(), cabinetId);
            return SummaryResponseDto.builder()
                    .periods(sortedPeriods)
                    .articles(mapToArticleSummaries(pageCards, itemRatingSupported))
                    .aggregatedMetrics(null)
                    .totalArticles((long) total)
                    .build();
        }

        Map<Integer, AggregatedMetricsDto> aggregatedMetrics = calculateAggregatedMetrics(
                visibleCards, sortedPeriods, seller.getId(), cabinetId);
        boolean itemRatingSupported = isItemRatingSupported(seller.getId(), cabinetId);
        List<WbProductCard> sortedCards = new ArrayList<>(visibleCards);
        sortProductCards(sortedCards, resolvedSortBy, resolvedSortDir);
        return SummaryResponseDto.builder()
                .periods(sortedPeriods)
                .articles(mapToArticleSummaries(sortedCards, itemRatingSupported))
                .aggregatedMetrics(aggregatedMetrics)
                .totalArticles(null)
                .build();
    }

    /**
     * Список артикулов кабинета/продавца — только справочная информация для попапа фильтра.
     * Если onlyWithPhoto == true — только артикулы с заполненным фото.
     */
    @Transactional(readOnly = true)
    public List<ArticleSummaryDto> getArticleList(
            User seller,
            Long cabinetId,
            Boolean onlyWithPhoto,
            Boolean onlyPriority,
            Boolean onlyInAdvertising
    ) {
        if (cabinetId != null) {
            Cabinet cabinet = cabinetService.findByIdWithUserOrThrow(cabinetId);
            if (cabinet.getMarketplaceType() == MarketplaceType.OZON) {
                return getOzonArticleList(cabinetId, onlyWithPhoto, onlyInAdvertising);
            }
        }
        List<WbProductCard> allCards = applyCatalogFilters(
                getVisibleCards(seller.getId(), cabinetId, null),
                seller.getId(),
                cabinetId,
                onlyWithPhoto,
                onlyPriority,
                onlyInAdvertising
        );
        sortProductCards(allCards, ArticleSummarySortField.WB_CREATED_AT, Sort.Direction.DESC);
        return mapToArticleSummaries(allCards, isItemRatingSupported(seller.getId(), cabinetId));
    }

    private List<ArticleSummaryDto> getOzonArticleList(
            Long cabinetId,
            Boolean onlyWithPhoto,
            Boolean onlyInAdvertising
    ) {
        List<OzonProductCard> cards = getVisibleOzonCards(cabinetId, null, onlyWithPhoto);
        if (Boolean.TRUE.equals(onlyInAdvertising)) {
            Set<Long> advertised = new HashSet<>(ozonCampaignArticleRepository.findActiveProductIdsByCabinetId(cabinetId));
            cards = cards.stream()
                    .filter(c -> c.getProductId() != null && advertised.contains(c.getProductId()))
                    .toList();
        }
        if (cards.isEmpty()) {
            return List.of();
        }

        Map<Long, OzonProductPriceHistory> priceByProductId = loadLatestOzonPrices(cabinetId);
        Map<Long, int[]> stocksByProductId = loadOzonStockTotals(cabinetId);
        Map<Long, OzonAnalyticsTotals> analyticsByProductId = loadOzonAnalyticsTotals(cabinetId);

        return cards.stream()
                .map(card -> mapOzonToArticleSummary(
                        card,
                        priceByProductId.get(card.getProductId()),
                        stocksByProductId.get(card.getProductId()),
                        analyticsByProductId.get(card.getProductId())
                ))
                .toList();
    }

    /**
     * Последний снимок цен по кабинету (макс. дата), индекс по product_id.
     */
    private Map<Long, OzonProductPriceHistory> loadLatestOzonPrices(Long cabinetId) {
        Optional<LocalDate> maxDate = ozonProductPriceHistoryRepository.findMaxDateByCabinetId(cabinetId);
        if (maxDate.isEmpty()) {
            return Map.of();
        }
        return ozonProductPriceHistoryRepository.findByCabinet_IdAndDate(cabinetId, maxDate.get()).stream()
                .collect(Collectors.toMap(OzonProductPriceHistory::getProductId, p -> p, (a, b) -> a));
    }

    /**
     * Суммы present по типам склада: [0]=FBO, [1]=FBS.
     */
    private Map<Long, int[]> loadOzonStockTotals(Long cabinetId) {
        Map<Long, int[]> result = new HashMap<>();
        for (OzonProductStock stock : ozonProductStockRepository.findByCabinet_Id(cabinetId)) {
            int[] totals = result.computeIfAbsent(stock.getProductId(), id -> new int[2]);
            int present = stock.getPresent() != null ? stock.getPresent() : 0;
            String type = stock.getStockType() != null ? stock.getStockType().trim().toLowerCase() : "";
            if ("fbo".equals(type)) {
                totals[0] += present;
            } else if ("fbs".equals(type)) {
                totals[1] += present;
            }
        }
        return result;
    }

    /**
     * Агрегаты ordered_units / revenue за последние 14 дней (как период sync).
     */
    private Map<Long, OzonAnalyticsTotals> loadOzonAnalyticsTotals(Long cabinetId) {
        LocalDate dateTo = LocalDate.now().minusDays(1);
        LocalDate dateFrom = dateTo.minusDays(13);
        Map<Long, OzonAnalyticsTotals> result = new HashMap<>();
        for (OzonProductCardAnalytics row : ozonProductCardAnalyticsRepository.findByCabinet_IdAndDateBetween(
                cabinetId, dateFrom, dateTo)) {
            OzonAnalyticsTotals totals = result.computeIfAbsent(row.getProductId(), id -> new OzonAnalyticsTotals());
            if (row.getOrderedUnits() != null) {
                totals.orderedUnits += row.getOrderedUnits();
            }
            if (row.getRevenue() != null) {
                totals.revenue = totals.revenue.add(row.getRevenue());
            }
        }
        return result;
    }

    private ArticleSummaryDto mapOzonToArticleSummary(
            OzonProductCard card,
            OzonProductPriceHistory price,
            int[] stockTotals,
            OzonAnalyticsTotals analytics
    ) {
        ArticleSummaryDto.ArticleSummaryDtoBuilder builder = ArticleSummaryDto.builder()
                .nmId(card.getProductId())
                .productId(card.getProductId())
                .offerId(card.getOfferId())
                .marketplaceType(MarketplaceType.OZON)
                .title(card.getTitle())
                .vendorCode(card.getOfferId())
                .photoTm(card.getPhotoUrl());
        if (price != null) {
            builder.price(price.getPrice())
                    .oldPrice(price.getOldPrice())
                    .priceDate(price.getDate());
        }
        if (stockTotals != null) {
            builder.stockFbo(stockTotals[0]).stockFbs(stockTotals[1]);
        } else {
            builder.stockFbo(0).stockFbs(0);
        }
        if (analytics != null) {
            builder.orderedUnits(analytics.orderedUnits)
                    .revenue(analytics.revenue);
        } else {
            builder.orderedUnits(0).revenue(BigDecimal.ZERO);
        }
        return builder.build();
    }

    private static final class OzonAnalyticsTotals {
        private int orderedUnits;
        private BigDecimal revenue = BigDecimal.ZERO;
    }

    /**
     * Сводная аналитика Ozon: заказы и выручка по периодам из {@code ozon_product_card_analytics}.
     */
    private SummaryResponseDto getOzonSummary(
            Long cabinetId,
            List<PeriodDto> periods,
            List<Long> excludedNmIds,
            Integer page,
            Integer size,
            String search,
            List<Long> includedNmIds,
            Boolean filterToNone,
            Boolean onlyWithPhoto,
            Boolean onlyInAdvertising,
            String sortDir
    ) {
        List<OzonProductCard> visibleCards = getVisibleOzonCards(cabinetId, excludedNmIds, onlyWithPhoto);
        if (Boolean.TRUE.equals(onlyInAdvertising)) {
            Set<Long> advertised = new HashSet<>(ozonCampaignArticleRepository.findActiveProductIdsByCabinetId(cabinetId));
            visibleCards = visibleCards.stream()
                    .filter(card -> card.getProductId() != null && advertised.contains(card.getProductId()))
                    .collect(Collectors.toList());
        }
        Sort.Direction resolvedSortDir = Sort.Direction.fromOptionalString(sortDir).orElse(Sort.Direction.DESC);

        boolean paginated = page != null && size != null && size > 0;
        if (paginated) {
            List<OzonProductCard> filtered = visibleCards;
            if (Boolean.TRUE.equals(filterToNone)) {
                filtered = List.of();
            } else if (includedNmIds != null && !includedNmIds.isEmpty()) {
                Set<Long> idSet = new HashSet<>(includedNmIds);
                filtered = filtered.stream()
                        .filter(card -> card.getProductId() != null && idSet.contains(card.getProductId()))
                        .collect(Collectors.toList());
            }
            if (search != null && !search.isBlank()) {
                filtered = filterOzonCardsBySearch(filtered, search.trim());
            }
            sortOzonProductCards(filtered, resolvedSortDir);
            int total = filtered.size();
            int from = Math.min(page * size, total);
            int to = Math.min(from + size, total);
            List<OzonProductCard> pageCards = from < to ? filtered.subList(from, to) : List.of();
            return SummaryResponseDto.builder()
                    .periods(periods)
                    .articles(mapOzonCardsToArticleSummaries(pageCards, cabinetId))
                    .aggregatedMetrics(null)
                    .totalArticles((long) total)
                    .build();
        }

        Set<Long> productIds = visibleCards.stream()
                .map(OzonProductCard::getProductId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Integer, AggregatedMetricsDto> aggregatedMetrics = calculateOzonAggregatedMetrics(
                cabinetId, productIds, periods);
        List<OzonProductCard> sortedCards = new ArrayList<>(visibleCards);
        sortOzonProductCards(sortedCards, resolvedSortDir);
        return SummaryResponseDto.builder()
                .periods(periods)
                .articles(mapOzonCardsToArticleSummaries(sortedCards, cabinetId))
                .aggregatedMetrics(aggregatedMetrics)
                .totalArticles(null)
                .build();
    }

    private MetricGroupResponseDto getOzonMetricGroup(
            Long cabinetId,
            String metricName,
            List<PeriodDto> periods,
            List<Long> excludedNmIds,
            Boolean onlyWithPhoto
    ) {
        if (!MetricNames.ORDERS.equals(metricName) && !MetricNames.ORDERS_AMOUNT.equals(metricName)) {
            return MetricGroupResponseDto.builder()
                    .metricName(metricName)
                    .metricNameRu(MetricNames.getRussianName(metricName))
                    .category(getMetricCategory(metricName))
                    .articles(List.of())
                    .build();
        }

        List<OzonProductCard> visibleCards = getVisibleOzonCards(cabinetId, excludedNmIds, onlyWithPhoto);
        Map<Long, List<OzonProductCardAnalytics>> analyticsByProductId = loadOzonAnalyticsGroupedByProduct(
                cabinetId,
                visibleCards.stream().map(OzonProductCard::getProductId).filter(Objects::nonNull).collect(Collectors.toSet()),
                periods
        );

        List<ArticleMetricDto> articleMetrics = visibleCards.stream()
                .map(card -> buildOzonArticleMetric(card, metricName, periods, analyticsByProductId))
                .collect(Collectors.toList());

        return MetricGroupResponseDto.builder()
                .metricName(metricName)
                .metricNameRu(MetricNames.getRussianName(metricName))
                .category(getMetricCategory(metricName))
                .articles(articleMetrics)
                .build();
    }

    private List<OzonProductCard> getVisibleOzonCards(
            Long cabinetId,
            List<Long> excludedNmIds,
            Boolean onlyWithPhoto
    ) {
        List<OzonProductCard> cards = ozonProductCardRepository.findByCabinet_IdOrderByProductIdAsc(cabinetId);
        if (Boolean.TRUE.equals(onlyWithPhoto)) {
            cards = cards.stream()
                    .filter(card -> card.getPhotoUrl() != null && !card.getPhotoUrl().isBlank())
                    .collect(Collectors.toList());
        }
        if (excludedNmIds != null && !excludedNmIds.isEmpty()) {
            Set<Long> excluded = new HashSet<>(excludedNmIds);
            cards = cards.stream()
                    .filter(card -> card.getProductId() != null && !excluded.contains(card.getProductId()))
                    .collect(Collectors.toList());
        }
        return cards;
    }

    private Map<Integer, AggregatedMetricsDto> calculateOzonAggregatedMetrics(
            Long cabinetId,
            Set<Long> productIds,
            List<PeriodDto> periods
    ) {
        Map<Integer, AggregatedMetricsDto> result = new HashMap<>();
        if (periods.isEmpty()) {
            return result;
        }
        Map<Long, List<OzonProductCardAnalytics>> analyticsByProductId = loadOzonAnalyticsGroupedByProduct(
                cabinetId, productIds, periods);
        for (PeriodDto period : periods) {
            int orders = 0;
            BigDecimal ordersAmount = BigDecimal.ZERO;
            for (Long productId : productIds) {
                OzonPeriodTotals totals = sumOzonAnalyticsForPeriod(
                        analyticsByProductId.getOrDefault(productId, List.of()), period);
                orders += totals.orderedUnits();
                ordersAmount = ordersAmount.add(totals.revenue());
            }
            AggregatedMetricsDto metrics = new AggregatedMetricsDto();
            metrics.setOrders(orders);
            metrics.setOrdersAmount(ordersAmount);
            result.put(period.getId(), metrics);
        }
        return result;
    }

    private Map<Long, List<OzonProductCardAnalytics>> loadOzonAnalyticsGroupedByProduct(
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

    private OzonPeriodTotals sumOzonAnalyticsForPeriod(
            List<OzonProductCardAnalytics> rows,
            PeriodDto period
    ) {
        int orderedUnits = 0;
        BigDecimal revenue = BigDecimal.ZERO;
        for (OzonProductCardAnalytics row : rows) {
            if (row.getDate().isBefore(period.getDateFrom()) || row.getDate().isAfter(period.getDateTo())) {
                continue;
            }
            if (row.getOrderedUnits() != null) {
                orderedUnits += row.getOrderedUnits();
            }
            if (row.getRevenue() != null) {
                revenue = revenue.add(row.getRevenue());
            }
        }
        return new OzonPeriodTotals(orderedUnits, revenue);
    }

    private ArticleMetricDto buildOzonArticleMetric(
            OzonProductCard card,
            String metricName,
            List<PeriodDto> periods,
            Map<Long, List<OzonProductCardAnalytics>> analyticsByProductId
    ) {
        List<OzonProductCardAnalytics> rows = analyticsByProductId.getOrDefault(card.getProductId(), List.of());
        List<PeriodMetricValueDto> periodValues = new ArrayList<>();
        for (PeriodDto period : periods) {
            OzonPeriodTotals totals = sumOzonAnalyticsForPeriod(rows, period);
            Object value = MetricNames.ORDERS.equals(metricName)
                    ? totals.orderedUnits()
                    : totals.revenue();
            BigDecimal changePercent = calculateOzonChangePercent(metricName, period, periods, rows);
            periodValues.add(PeriodMetricValueDto.builder()
                    .periodId(period.getId())
                    .value(value)
                    .changePercent(changePercent)
                    .build());
        }
        return ArticleMetricDto.builder()
                .nmId(card.getProductId())
                .photoTm(card.getPhotoUrl())
                .periods(periodValues)
                .build();
    }

    private BigDecimal calculateOzonChangePercent(
            String metricName,
            PeriodDto period,
            List<PeriodDto> sortedPeriods,
            List<OzonProductCardAnalytics> rows
    ) {
        OzonPeriodTotals currentTotals = sumOzonAnalyticsForPeriod(rows, period);
        Object currentValue = MetricNames.ORDERS.equals(metricName)
                ? currentTotals.orderedUnits()
                : currentTotals.revenue();
        if (currentValue == null) {
            return null;
        }
        PeriodDto previousPeriod = findPreviousPeriodByDateOrder(period, sortedPeriods);
        if (previousPeriod == null) {
            return null;
        }
        OzonPeriodTotals previousTotals = sumOzonAnalyticsForPeriod(rows, previousPeriod);
        Object previousValue = MetricNames.ORDERS.equals(metricName)
                ? previousTotals.orderedUnits()
                : previousTotals.revenue();
        return calculatePercentageChange(currentValue, previousValue);
    }

    private List<ArticleSummaryDto> mapOzonCardsToArticleSummaries(
            List<OzonProductCard> cards,
            Long cabinetId
    ) {
        if (cards.isEmpty()) {
            return List.of();
        }
        Map<Long, OzonProductPriceHistory> priceByProductId = loadLatestOzonPrices(cabinetId);
        Map<Long, int[]> stocksByProductId = loadOzonStockTotals(cabinetId);
        return cards.stream()
                .map(card -> mapOzonToArticleSummary(
                        card,
                        priceByProductId.get(card.getProductId()),
                        stocksByProductId.get(card.getProductId()),
                        null
                ))
                .toList();
    }

    private void sortOzonProductCards(List<OzonProductCard> cards, Sort.Direction sortDir) {
        Comparator<OzonProductCard> comparator = Comparator.comparing(
                OzonProductCard::getProductId,
                Comparator.nullsLast(Comparator.naturalOrder())
        );
        if (sortDir == Sort.Direction.DESC) {
            comparator = comparator.reversed();
        }
        cards.sort(comparator);
    }

    private List<OzonProductCard> filterOzonCardsBySearch(List<OzonProductCard> cards, String searchLower) {
        String lower = searchLower.toLowerCase();
        return cards.stream()
                .filter(card ->
                        (card.getTitle() != null && card.getTitle().toLowerCase().contains(lower))
                                || (card.getOfferId() != null && card.getOfferId().toLowerCase().contains(lower))
                                || (card.getProductId() != null && String.valueOf(card.getProductId()).contains(lower))
                )
                .collect(Collectors.toList());
    }

    private record OzonPeriodTotals(int orderedUnits, BigDecimal revenue) {
    }

    private List<WbProductCard> filterCardsBySearch(List<WbProductCard> cards, String searchLower) {
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
            Boolean onlyWithPhoto,
            Boolean onlyPriority,
            Boolean onlyInAdvertising
    ) {
        List<PeriodDto> sortedPeriods = sortPeriodsByDateFrom(periods);
        if (cabinetId != null) {
            Cabinet cabinet = cabinetService.findByIdWithUserOrThrow(cabinetId);
            if (cabinet.getMarketplaceType() == MarketplaceType.OZON) {
                return getOzonMetricGroup(
                        cabinetId,
                        metricName,
                        sortedPeriods,
                        excludedNmIds,
                        onlyWithPhoto
                );
            }
        }
        if (isAdvertisingMetric(metricName)) {
            return getAdvertisingMetricGroup(
                    seller, cabinetId, metricName, sortedPeriods, excludedNmIds,
                    onlyWithPhoto, onlyPriority, onlyInAdvertising
            );
        } else {
            return getFunnelMetricGroup(
                    seller, cabinetId, metricName, sortedPeriods, excludedNmIds,
                    onlyWithPhoto, onlyPriority, onlyInAdvertising
            );
        }
    }

    private MetricGroupResponseDto getFunnelMetricGroup(
            User seller,
            Long cabinetId,
            String metricName,
            List<PeriodDto> periods,
            List<Long> excludedNmIds,
            Boolean onlyWithPhoto,
            Boolean onlyPriority,
            Boolean onlyInAdvertising
    ) {
        List<WbProductCard> visibleCards = applyCatalogFilters(
                getVisibleCards(seller.getId(), cabinetId, excludedNmIds),
                seller.getId(),
                cabinetId,
                onlyWithPhoto,
                onlyPriority,
                onlyInAdvertising
        );

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
            Boolean onlyWithPhoto,
            Boolean onlyPriority,
            Boolean onlyInAdvertising
    ) {
        List<WbProductCard> visibleCards = applyCatalogFilters(
                getVisibleCards(seller.getId(), cabinetId, excludedNmIds),
                seller.getId(),
                cabinetId,
                onlyWithPhoto,
                onlyPriority,
                onlyInAdvertising
        );
        List<Long> campaignIds = getCampaignIdsForCabinet(seller.getId(), cabinetId);

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
        List<WbPromotionCampaign> campaigns = cabinetId != null
                ? campaignRepository.findByCabinet_Id(cabinetId)
                : campaignRepository.findByCabinet_User_Id(sellerId);
        return campaigns.stream().map(WbPromotionCampaign::getAdvertId).collect(Collectors.toList());
    }

    private BigDecimal calculateArticleAdvertisingChangePercent(
            Long nmId,
            String metricName,
            PeriodDto period,
            List<PeriodDto> allPeriodsSortedByDate,
            Map<PeriodDto, Map<Long, WbCampaignStatisticsAggregator.AdvertisingStats>> statsByPeriodByArticle,
            Map<PeriodDto, Map<Long, BigDecimal>> funnelOrdersAmountByPeriodByArticle
    ) {
        PeriodDto previousPeriod = findPreviousPeriodByDateOrder(period, allPeriodsSortedByDate);
        if (previousPeriod == null) {
            return null;
        }
        WbCampaignStatisticsAggregator.AdvertisingStats currentStats = statsByPeriodByArticle
                .getOrDefault(period, Collections.emptyMap())
                .getOrDefault(nmId, new WbCampaignStatisticsAggregator.AdvertisingStats(0, 0, BigDecimal.ZERO, 0, BigDecimal.ZERO));
        WbCampaignStatisticsAggregator.AdvertisingStats previousStats = statsByPeriodByArticle
                .getOrDefault(previousPeriod, Collections.emptyMap())
                .getOrDefault(nmId, new WbCampaignStatisticsAggregator.AdvertisingStats(0, 0, BigDecimal.ZERO, 0, BigDecimal.ZERO));
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

        WbCampaignStatisticsAggregator.AdvertisingStats currentStats = 
                campaignStatisticsAggregator.aggregateStatsForCampaign(campaignId, period);
        WbCampaignStatisticsAggregator.AdvertisingStats previousStats = 
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
    
    private Map<PeriodDto, WbCampaignStatisticsAggregator.AdvertisingStats> preloadAdvertisingStats(
            Long sellerId,
            Long cabinetId,
            List<PeriodDto> periods
    ) {
        Map<PeriodDto, WbCampaignStatisticsAggregator.AdvertisingStats> cache = new HashMap<>();
        List<WbPromotionCampaign> campaigns = cabinetId != null
                ? campaignRepository.findByCabinet_Id(cabinetId)
                : campaignRepository.findByCabinet_User_Id(sellerId);
        List<Long> campaignIds = campaigns.stream()
                .map(WbPromotionCampaign::getAdvertId)
                .collect(Collectors.toList());

        for (PeriodDto period : periods) {
            WbCampaignStatisticsAggregator.AdvertisingStats stats =
                    campaignStatisticsAggregator.aggregateStats(campaignIds, period);
            cache.put(period, stats);
        }

        return cache;
    }

    /**
     * Получает детальную информацию по артикулу.
     * @param campaignDateFrom начало периода для метрик РК (опционально)
     * @param campaignDateTo конец периода для метрик РК (опционально)
     * @param dailyDataDateFrom начало диапазона для {@code dailyData} (опционально, вместе с {@code dailyDataDateTo})
     * @param dailyDataDateTo конец диапазона для {@code dailyData} включительно (опционально)
     * @param dailyDataCampaignAdvertId если задан — рекламные метрики в {@code dailyData} только по этой РК
     */
    @Transactional(readOnly = true)
    public ArticleResponseDto getArticle(User seller, Long cabinetId, Long nmId, List<PeriodDto> periods,
                                         LocalDate campaignDateFrom, LocalDate campaignDateTo,
                                         LocalDate dailyDataDateFrom, LocalDate dailyDataDateTo,
                                         Long dailyDataCampaignAdvertId) {
        if (cabinetId != null) {
            Cabinet cabinet = cabinetService.findByIdWithUserOrThrow(cabinetId);
            if (cabinet.getMarketplaceType() == MarketplaceType.OZON) {
                return getOzonArticle(
                        cabinetId,
                        nmId,
                        periods,
                        campaignDateFrom,
                        campaignDateTo,
                        dailyDataDateFrom,
                        dailyDataDateTo
                );
            }
        }
        WbProductCard card = findCardBySeller(nmId, seller.getId(), cabinetId);
        Long cardCabinetId = card.getCabinet() != null ? card.getCabinet().getId() : null;

        List<DailyDataDto> dailyData = getDailyData(nmId, cardCabinetId, dailyDataDateFrom, dailyDataDateTo, dailyDataCampaignAdvertId);
        List<WbPromotionParticipation> participations = cardCabinetId != null
                ? promotionParticipationRepository.findByCabinet_IdAndNmId(cardCabinetId, nmId)
                : Collections.emptyList();
        List<String> wbPromotionNames = participations.stream()
                .map(WbPromotionParticipation::getWbPromotionName)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        List<String> wbPromotionTypes = participations.stream()
                .map(WbPromotionParticipation::getWbPromotionType)
                .map(t -> t != null ? t : "")
                .collect(Collectors.toList());
        Boolean inWbPromotion = !wbPromotionNames.isEmpty();
        boolean itemRatingSupported = isItemRatingSupported(seller.getId(), cabinetId != null ? cabinetId : cardCabinetId);
        List<ArticleSummaryDto> bundleProducts = getBundleProducts(card, cardCabinetId, itemRatingSupported);
        LocalDateTime lastStocksUpdateTriggeredAt = cardCabinetId != null
                ? cabinetService.findById(cardCabinetId).map(Cabinet::getLastStocksUpdateRequestedAt).orElse(null)
                : null;
        String articleGoal = cardCabinetId != null
                ? articleGoalService.findGoalText(cardCabinetId, nmId).orElse(null)
                : null;

        return ArticleResponseDto.builder()
                .article(mapToArticleDetail(card, itemRatingSupported))
                .periods(periods)
                .metrics(calculateAllMetrics(card, periods, seller.getId(), cardCabinetId))
                .dailyData(dailyData)
                .campaigns(getCampaigns(nmId, cardCabinetId, campaignDateFrom, campaignDateTo))
                .inWbPromotion(inWbPromotion)
                .wbPromotionNames(wbPromotionNames)
                .wbPromotionTypes(wbPromotionTypes)
                .stocks(getStocks(nmId, cardCabinetId))
                .fbsStocks(getFbsStocks(nmId, cardCabinetId))
                .bundleProducts(bundleProducts)
                .lastStocksUpdateTriggeredAt(lastStocksUpdateTriggeredAt)
                .articleGoal(articleGoal)
                .build();
    }

    /**
     * Товары «в связке» — другие артикулы с тем же IMT ID в том же кабинете, без текущего nmId.
     */
    private List<ArticleSummaryDto> getBundleProducts(WbProductCard card, Long cardCabinetId, boolean itemRatingSupported) {
        if (card.getImtId() == null) {
            return java.util.Collections.emptyList();
        }
        Long cabinetId = cardCabinetId != null ? cardCabinetId
                : (card.getCabinet() != null ? card.getCabinet().getId() : null);
        if (cabinetId == null) {
            return java.util.Collections.emptyList();
        }
        List<WbProductCard> sameImt = productCardRepository.findByImtIdAndCabinet_Id(card.getImtId(), cabinetId);
        return sameImt.stream()
                .filter(c -> !c.getNmId().equals(card.getNmId()))
                .map(c -> mapToArticleSummary(c, itemRatingSupported))
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

    private List<WbProductCard> getVisibleCards(Long sellerId, Long cabinetId, List<Long> excludedNmIds) {
        List<WbProductCard> allCards = cabinetId != null
                ? productCardRepository.findByCabinet_Id(cabinetId)
                : productCardRepository.findByCabinet_User_Id(sellerId);
        return WbProductCardFilter.filterVisibleCards(allCards, excludedNmIds).stream()
                .collect(Collectors.toList());
    }

    /**
     * Фильтры каталога артикулов: фото, приоритет, участие в незавершённых РК.
     */
    private List<WbProductCard> applyCatalogFilters(
            List<WbProductCard> cards,
            Long sellerId,
            Long cabinetId,
            Boolean onlyWithPhoto,
            Boolean onlyPriority,
            Boolean onlyInAdvertising
    ) {
        List<WbProductCard> result = cards;
        if (Boolean.TRUE.equals(onlyWithPhoto)) {
            result = result.stream()
                    .filter(this::cardHasAnyPhoto)
                    .collect(Collectors.toList());
        }
        if (Boolean.TRUE.equals(onlyPriority)) {
            result = result.stream()
                    .filter(c -> Boolean.TRUE.equals(c.getIsPriority()))
                    .collect(Collectors.toList());
        }
        if (Boolean.TRUE.equals(onlyInAdvertising)) {
            Set<Long> advertisedNmIds = getNmIdsInNonFinishedCampaigns(sellerId, cabinetId);
            result = result.stream()
                    .filter(c -> c.getNmId() != null && advertisedNmIds.contains(c.getNmId()))
                    .collect(Collectors.toList());
        }
        return result;
    }

    /**
     * nmId артикулов, привязанных к незавершённым РК (по кабинету или всем кабинетам продавца).
     */
    private Set<Long> getNmIdsInNonFinishedCampaigns(Long sellerId, Long cabinetId) {
        if (cabinetId != null) {
            return campaignArticleRepository.findDistinctNmIdsByCabinetIdExcludingFinishedCampaigns(cabinetId);
        }
        return campaignArticleRepository.findDistinctNmIdsBySellerIdExcludingFinishedCampaigns(sellerId);
    }

    private void sortProductCards(
            List<WbProductCard> cards,
            ArticleSummarySortField sortBy,
            Sort.Direction sortDir
    ) {
        ArticleSummarySortField effectiveSortBy = sortBy != null ? sortBy : ArticleSummarySortField.WB_CREATED_AT;
        Sort.Direction effectiveSortDir = sortDir != null ? sortDir : Sort.Direction.DESC;
        Comparator<WbProductCard> comparator = switch (effectiveSortBy) {
            case WB_CREATED_AT -> Comparator.comparing(
                    WbProductCard::getWbCreatedAt,
                    Comparator.nullsLast(Comparator.naturalOrder())
            );
        };
        if (effectiveSortDir == Sort.Direction.DESC) {
            comparator = comparator.reversed();
        }
        cards.sort(comparator);
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
                .map(period -> calculatePeriodMetricValue(card, metricName, period, periods, sellerId, cabinetId, advertisingStatsCache))
                .collect(Collectors.toList());

        return ArticleMetricDto.builder()
                .nmId(card.getNmId())
                .photoTm(card.getPhotoTm())
                .periods(periodValues)
                .build();
    }

    private PeriodMetricValueDto calculatePeriodMetricValue(
            WbProductCard card,
            String metricName,
            PeriodDto period,
            List<PeriodDto> allPeriods,
            Long sellerId,
            Long cabinetId,
            Map<PeriodDto, WbCampaignStatisticsAggregator.AdvertisingStats> advertisingStatsCache
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
            WbProductCard card,
            String metricName,
            PeriodDto period,
            List<PeriodDto> allPeriodsSortedByDate,
            Long sellerId,
            Long cabinetId,
            Object currentValue,
            Map<PeriodDto, WbCampaignStatisticsAggregator.AdvertisingStats> advertisingStatsCache
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

    private List<MetricDto> calculateAllMetrics(WbProductCard card, List<PeriodDto> periods, Long sellerId, Long cabinetId) {
        Map<PeriodDto, WbCampaignStatisticsAggregator.AdvertisingStats> advertisingStatsCache =
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
    private static WbProductPriceHistory pickRepresentativePriceRow(List<WbProductPriceHistory> prices) {
        if (prices == null || prices.isEmpty()) {
            throw new IllegalArgumentException("prices must be non-empty");
        }
        Optional<WbProductPriceHistory> consolidatedWithSpp = prices.stream()
                .filter(p -> p.getSizeId() == null && p.getSppDiscount() != null)
                .findFirst();
        if (consolidatedWithSpp.isPresent()) {
            return consolidatedWithSpp.get();
        }
        Optional<WbProductPriceHistory> anyWithSpp = prices.stream()
                .filter(p -> p.getSppDiscount() != null)
                .findFirst();
        if (anyWithSpp.isPresent()) {
            return anyWithSpp.get();
        }
        Optional<WbProductPriceHistory> withoutSize = prices.stream()
                .filter(p -> p.getSizeId() == null)
                .findFirst();
        return withoutSize.orElseGet(() -> prices.get(0));
    }

    /**
     * Суточные строки для графика/таблицы: либо явный диапазон {@code from}/{@code to}, либо последние {@value #DEFAULT_DAILY_DATA_SPAN_DAYS} дней до вчера.
     * Конец не позже «вчера»; длина ограничена {@value #MAX_DAILY_DATA_SPAN_DAYS} днями (при перегрузе отрезается начало интервала).
     */
    private List<DailyDataDto> getDailyData(Long nmId, Long cabinetId, LocalDate from, LocalDate to, Long campaignAdvertId) {
        AnalyticsDateRange range = resolveAnalyticsDateRange(from, to);
        LocalDate startDate = range.startDate();
        LocalDate endDate = range.endDate();

        List<WbProductCardAnalytics> funnelData = cabinetId != null
                ? analyticsRepository.findByCabinet_IdAndProductCardNmIdAndDateBetween(cabinetId, nmId, startDate, endDate)
                : analyticsRepository.findByProductCardNmIdAndDateBetween(nmId, startDate, endDate);

        List<WbPromotionCampaignStatistics> advertisingData = resolveAdvertisingStatsForDailyData(
                nmId, cabinetId, startDate, endDate, campaignAdvertId);

        List<WbProductPriceHistory> priceData = cabinetId != null
                ? priceHistoryRepository.findByNmIdAndDateBetweenAndCabinet_Id(nmId, startDate, endDate, cabinetId)
                : priceHistoryRepository.findByNmIdAndDateBetween(nmId, startDate, endDate);
        
        // Группируем рекламные данные по датам
        Map<LocalDate, AdvertisingDailyStats> advertisingByDate = advertisingData.stream()
                .collect(Collectors.groupingBy(
                        WbPromotionCampaignStatistics::getDate,
                        Collectors.collectingAndThen(
                                Collectors.toList(),
                                stats -> aggregateAdvertisingStats(stats)
                        )
                ));
        
        // Группируем данные ценообразования по датам (одна строка на день для таблицы)
        Map<LocalDate, WbProductPriceHistory> priceByDate = priceData.stream()
                .collect(Collectors.groupingBy(
                        WbProductPriceHistory::getDate,
                        Collectors.collectingAndThen(
                                Collectors.toList(),
                                AnalyticsService::pickRepresentativePriceRow
                        )
                ));

        // Создаем мапу для быстрого поиска данных воронки
        Map<LocalDate, WbProductCardAnalytics> funnelByDate = funnelData.stream()
                .collect(Collectors.toMap(
                        WbProductCardAnalytics::getDate,
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
                    WbProductCardAnalytics funnel = funnelByDate.get(date);
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
                        // СРО и ДРР по «Заказали» из воронки — как в колонке таблицы и при агрегации «Все»
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
                    
                    WbProductPriceHistory price = priceByDate.get(date);
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

    /**
     * Рекламная статистика для {@code dailyData}: по всем РК артикула или только по указанной кампании кабинета.
     */
    private List<WbPromotionCampaignStatistics> resolveAdvertisingStatsForDailyData(
            Long nmId,
            Long cabinetId,
            LocalDate startDate,
            LocalDate endDate,
            Long campaignAdvertId
    ) {
        if (campaignAdvertId == null) {
            return campaignStatisticsRepository.findByNmIdAndDateBetween(nmId, startDate, endDate);
        }
        if (cabinetId == null) {
            return Collections.emptyList();
        }
        return campaignRepository.findByAdvertIdAndCabinet_Id(campaignAdvertId, cabinetId)
                .map(c -> campaignStatisticsRepository.findByCampaignAdvertIdAndNmIdAndDateBetween(
                        campaignAdvertId, nmId, startDate, endDate))
                .orElse(Collections.emptyList());
    }

    private AdvertisingDailyStats aggregateAdvertisingStats(List<WbPromotionCampaignStatistics> stats) {
        int views = 0;
        int clicks = 0;
        BigDecimal sum = BigDecimal.ZERO;
        int orders = 0;
        BigDecimal ordersSum = BigDecimal.ZERO;
        
        for (WbPromotionCampaignStatistics stat : stats) {
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
        List<WbCampaignArticle> campaignArticles = campaignArticleRepository.findByNmId(nmId);

        List<WbPromotionCampaign> campaigns = campaignArticles.stream()
                .map(WbCampaignArticle::getCampaign)
                .filter(Objects::nonNull)
                .filter(campaign -> campaign.getStatus() != WbCampaignStatus.FINISHED)
                .filter(campaign -> cabinetId == null || (campaign.getCabinet() != null && campaign.getCabinet().getId().equals(cabinetId)))
                .distinct()
                .collect(Collectors.toList());

        if (campaigns.isEmpty()) {
            return Collections.emptyList();
        }

        boolean withMetrics = campaignDateFrom != null && campaignDateTo != null;
        AnalyticsDateRange metricsRange = withMetrics
                ? resolveAnalyticsDateRange(campaignDateFrom, campaignDateTo)
                : null;
        Map<Long, List<WbPromotionCampaignStatistics>> statsByCampaign = new HashMap<>();
        if (withMetrics && metricsRange != null) {
            List<Long> campaignIds = campaigns.stream().map(WbPromotionCampaign::getAdvertId).collect(Collectors.toList());
            List<WbPromotionCampaignStatistics> allStats = campaignStatisticsRepository.findByCampaignAdvertIdInAndDateBetween(
                    campaignIds, metricsRange.startDate(), metricsRange.endDate());
            statsByCampaign = allStats.stream().collect(Collectors.groupingBy(s -> s.getCampaign().getAdvertId()));
        }

        final Map<Long, List<WbPromotionCampaignStatistics>> statsByCampaignFinal = statsByCampaign;
        final Set<Long> scopeNmIds = Set.of(nmId);
        return campaigns.stream()
                .map(c -> buildCampaignDto(c, scopeNmIds,
                        statsByCampaignFinal.getOrDefault(c.getAdvertId(), Collections.emptyList()),
                        withMetrics, null, null))
                .collect(Collectors.toList());
    }

    /**
     * Получает остатки товара на складах.
     */
    private List<StockDto> getStocks(Long nmId, Long cabinetId) {
        List<WbProductStock> stocks = cabinetId != null
                ? stockRepository.findByNmIdAndCabinet_Id(nmId, cabinetId)
                : stockRepository.findByNmId(nmId);
        
        if (stocks.isEmpty()) {
            return Collections.emptyList();
        }
        
        // Группируем по складам и суммируем количество
        Map<Long, StockAggregate> stockByWarehouse = stocks.stream()
                .collect(Collectors.groupingBy(
                        WbProductStock::getWarehouseId,
                        Collectors.collectingAndThen(
                                Collectors.toList(),
                                stockList -> {
                                    int totalAmount = stockList.stream()
                                            .mapToInt(WbProductStock::getAmount)
                                            .sum();
                                    LocalDateTime latestUpdate = stockList.stream()
                                            .map(WbProductStock::getUpdatedAt)
                                            .max(LocalDateTime::compareTo)
                                            .orElse(null);
                                    return new StockAggregate(totalAmount, latestUpdate);
                                }
                        )
                ));
        
        // Названия и флаги складов
        Map<Long, WbWarehouse> warehousesById = warehouseRepository.findAll().stream()
                .collect(Collectors.toMap(
                        w -> Long.valueOf(w.getId()),
                        w -> w,
                        (existing, replacement) -> existing
                ));
        
        // Формируем список DTO
        return stockByWarehouse.entrySet().stream()
                .map(entry -> {
                    Long warehouseId = entry.getKey();
                    StockAggregate aggregate = entry.getValue();
                    WbWarehouse warehouse = warehousesById.get(warehouseId);
                    String warehouseName = warehouse != null ? warehouse.getName() : "Склад " + warehouseId;
                    boolean onFire = warehouse != null && Boolean.TRUE.equals(warehouse.getOnFire());
                    
                    return StockDto.builder()
                            .warehouseId(warehouseId)
                            .warehouseName(warehouseName)
                            .onFire(onFire)
                            .amount(aggregate.getTotalAmount())
                            .updatedAt(aggregate.getLatestUpdate())
                            .build();
                })
                .sorted((a, b) -> b.getAmount().compareTo(a.getAmount())) // Сортируем по убыванию количества
                .collect(Collectors.toList());
    }

    /**
     * Остатки FBS артикула на складах продавца кабинета.
     * Показываем все склады продавца: если WB не вернул размеры этого nmID, количество будет 0.
     */
    private List<StockDto> getFbsStocks(Long nmId, Long cabinetId) {
        if (cabinetId == null) {
            return Collections.emptyList();
        }
        List<WbSellerWarehouse> warehouses = sellerWarehouseRepository.findByCabinet_Id(cabinetId).stream()
                .filter(warehouse -> !Boolean.TRUE.equals(warehouse.getIsDeleting()))
                .toList();
        if (warehouses.isEmpty()) {
            return Collections.emptyList();
        }

        List<WbProductFbsStock> stocks = fbsStockRepository.findByNmIdAndCabinet_Id(nmId, cabinetId);
        Map<Long, StockAggregate> stockByWarehouse = stocks.stream()
                .collect(Collectors.groupingBy(
                        WbProductFbsStock::getWarehouseId,
                        Collectors.collectingAndThen(
                                Collectors.toList(),
                                stockList -> {
                                    int totalAmount = stockList.stream()
                                            .mapToInt(WbProductFbsStock::getAmount)
                                            .sum();
                                    LocalDateTime latestUpdate = stockList.stream()
                                            .map(WbProductFbsStock::getUpdatedAt)
                                            .max(LocalDateTime::compareTo)
                                            .orElse(null);
                                    return new StockAggregate(totalAmount, latestUpdate);
                                }
                        )
                ));

        return warehouses.stream()
                .map(warehouse -> {
                    StockAggregate aggregate = stockByWarehouse.get(warehouse.getWarehouseId());
                    int amount = aggregate != null ? aggregate.getTotalAmount() : 0;
                    LocalDateTime updatedAt = aggregate != null && aggregate.getLatestUpdate() != null
                            ? aggregate.getLatestUpdate()
                            : warehouse.getUpdatedAt();
                    return StockDto.builder()
                            .warehouseId(warehouse.getWarehouseId())
                            .warehouseName(warehouse.getName())
                            .onFire(false)
                            .amount(amount)
                            .updatedAt(updatedAt)
                            .build();
                })
                .sorted((a, b) -> b.getAmount().compareTo(a.getAmount()))
                .collect(Collectors.toList());
    }

    private static final int DEFAULT_CAMPAIGNS_PERIOD_DAYS = 14;

    /**
     * Нормализованный период для {@code dailyData} и списка РК: конец не позже вчера, длина ограничена.
     */
    private AnalyticsDateRange resolveAnalyticsDateRange(LocalDate from, LocalDate to) {
        LocalDate yesterday = LocalDate.now().minusDays(1);
        LocalDate startDate;
        LocalDate endDate;
        if (from != null && to != null) {
            LocalDate a = from;
            LocalDate b = to;
            if (a.isAfter(b)) {
                LocalDate tmp = a;
                a = b;
                b = tmp;
            }
            endDate = b.isAfter(yesterday) ? yesterday : b;
            startDate = a;
            if (startDate.isAfter(endDate)) {
                startDate = endDate;
            }
            long span = ChronoUnit.DAYS.between(startDate, endDate) + 1;
            if (span > MAX_DAILY_DATA_SPAN_DAYS) {
                startDate = endDate.minusDays(MAX_DAILY_DATA_SPAN_DAYS - 1);
            }
        } else {
            endDate = yesterday;
            startDate = endDate.minusDays(DEFAULT_DAILY_DATA_SPAN_DAYS - 1);
        }
        return new AnalyticsDateRange(startDate, endDate);
    }

    private record AnalyticsDateRange(LocalDate startDate, LocalDate endDate) {
    }

    /**
     * DTO кампании с метриками за период.
     */
    private CampaignDto buildCampaignDto(
            WbPromotionCampaign c,
            Set<Long> scopeNmIds,
            List<WbPromotionCampaignStatistics> campaignStats,
            boolean withMetrics,
            Integer articlesCount,
            BidderStatus bidderStatus
    ) {
        CampaignDto.CampaignDtoBuilder builder = CampaignDto.builder()
                .id(c.getAdvertId())
                .name(c.getName())
                .type(c.getDisplayType())
                .status(c.getStatus() != null ? c.getStatus().getCode() : null)
                .statusName(c.getStatus() != null ? c.getStatus().getDescription() : null)
                .createdAt(c.getCreateTime())
                .updatedAt(resolveCampaignUpdatedAt(c));
        if (articlesCount != null) {
            builder.articlesCount(articlesCount);
        }
        if (bidderStatus != null) {
            builder.bidderStatus(bidderStatus.name());
        }
        if (withMetrics) {
            applyCampaignPeriodMetrics(builder, scopeNmIds, campaignStats);
        }
        return builder.build();
    }

    /**
     * Метрики кампании за период из fullstats WB: просмотры, клики, затраты, корзина (atbs), заказы.
     */
    private void applyCampaignPeriodMetrics(
            CampaignDto.CampaignDtoBuilder builder,
            Set<Long> scopeNmIds,
            List<WbPromotionCampaignStatistics> campaignStats
    ) {
        int views = 0;
        int clicks = 0;
        int cart = 0;
        int orders = 0;
        BigDecimal sum = BigDecimal.ZERO;

        for (WbPromotionCampaignStatistics s : campaignStats) {
            if (!scopeNmIds.isEmpty() && !scopeNmIds.contains(s.getNmId())) {
                continue;
            }
            views += MathUtils.getValueOrZero(s.getViews());
            clicks += MathUtils.getValueOrZero(s.getClicks());
            cart += MathUtils.getValueOrZero(s.getAtbs());
            orders += MathUtils.getValueOrZero(s.getOrders());
            if (s.getSum() != null) {
                sum = sum.add(s.getSum());
            }
        }

        BigDecimal ctr = MathUtils.calculatePercentage(clicks, views);
        BigDecimal cpc = clicks > 0
                ? sum.divide(BigDecimal.valueOf(clicks), 2, RoundingMode.HALF_UP)
                : null;

        builder.views(views > 0 ? views : null)
                .clicks(clicks > 0 ? clicks : null)
                .ctr(ctr)
                .cpc(cpc)
                .costs(sum.compareTo(BigDecimal.ZERO) > 0 ? sum : null)
                .cart(cart > 0 ? cart : null)
                .orders(orders > 0 ? orders : null);
    }

    private static LocalDateTime resolveCampaignUpdatedAt(WbPromotionCampaign c) {
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
    public List<CampaignDto> listCampaignsByCabinet(
            Long cabinetId,
            LocalDate dateFrom,
            LocalDate dateTo,
            User seller,
            Long nmIdFilter
    ) {
        if (cabinetId == null) {
            return Collections.emptyList();
        }
        AnalyticsDateRange range = resolveAnalyticsDateRange(dateFrom, dateTo);
        LocalDate from = range.startDate();
        LocalDate to = range.endDate();

        List<WbPromotionCampaign> campaigns = campaignRepository.findByCabinet_Id(cabinetId).stream()
                .filter(c -> c.getStatus() != WbCampaignStatus.FINISHED)
                .collect(Collectors.toList());
        if (campaigns.isEmpty()) {
            return Collections.emptyList();
        }
        List<Long> campaignIds = campaigns.stream().map(WbPromotionCampaign::getAdvertId).collect(Collectors.toList());
        List<WbPromotionCampaignStatistics> allStats = campaignStatisticsRepository.findByCampaignAdvertIdInAndDateBetween(
                campaignIds, from, to);
        Map<Long, List<WbPromotionCampaignStatistics>> statsByCampaign = allStats.stream()
                .collect(Collectors.groupingBy(s -> s.getCampaign().getAdvertId()));

        Map<Long, Set<Long>> nmIdsByCampaign = campaignArticleRepository.findByCampaignIdIn(campaignIds).stream()
                .collect(Collectors.groupingBy(
                        WbCampaignArticle::getCampaignId,
                        Collectors.mapping(WbCampaignArticle::getNmId, Collectors.toSet())
                ));

        List<Object[]> articleCounts = campaignArticleRepository.countByCampaignIdIn(campaignIds);
        Map<Long, Integer> articlesCountByCampaign = articleCounts.stream()
                .collect(Collectors.toMap(row -> (Long) row[0], row -> ((Number) row[1]).intValue()));

        Map<Long, WbCampaignManagementState> statesByCampaignId = campaignManagementStateRepository.findByCabinetId(cabinetId)
                .stream()
                .collect(Collectors.toMap(WbCampaignManagementState::getCampaignId, s -> s, (a, b) -> a));
        Map<Long, List<WbCampaignScheduleSlot>> slotsByCampaignId = campaignScheduleSlotRepository.findByCabinetId(cabinetId)
                .stream()
                .collect(Collectors.groupingBy(WbCampaignScheduleSlot::getCampaignId));
        Map<Long, BidderStatus> bidderStatuses = bidderStatusResolver.resolveForCabinet(
                cabinetId, seller, campaigns, statesByCampaignId, slotsByCampaignId);

        return campaigns.stream()
                .filter(c -> nmIdFilter == null
                        || nmIdsByCampaign.getOrDefault(c.getAdvertId(), Collections.emptySet()).contains(nmIdFilter))
                .map(c -> {
                    Long advertId = c.getAdvertId();
                    Set<Long> scopeNmIds = nmIdsByCampaign.getOrDefault(advertId, Collections.emptySet());
                    List<WbPromotionCampaignStatistics> stats = statsByCampaign.getOrDefault(advertId, Collections.emptyList());
                    Integer articlesCount = articlesCountByCampaign.getOrDefault(advertId, 0);
                    BidderStatus bidderStatus = bidderStatuses.get(advertId);
                    return buildCampaignDto(c, scopeNmIds, stats, true, articlesCount, bidderStatus);
                })
                .collect(Collectors.toList());
    }

    /**
     * Список рекламных кампаний Ozon из локальной БД с агрегацией дневной статистики за период.
     */
    @Transactional(readOnly = true)
    public List<CampaignDto> listOzonCampaignsByCabinet(Long cabinetId, LocalDate dateFrom, LocalDate dateTo) {
        if (cabinetId == null) {
            return Collections.emptyList();
        }
        AnalyticsDateRange range = resolveAnalyticsDateRange(dateFrom, dateTo);
        LocalDate from = range.startDate();
        LocalDate to = range.endDate();

        List<OzonPromotionCampaign> campaigns = ozonPromotionCampaignRepository.findByCabinet_Id(cabinetId).stream()
                .filter(c -> !"CAMPAIGN_STATE_FINISHED".equals(c.getState()))
                .collect(Collectors.toList());
        if (campaigns.isEmpty()) {
            return Collections.emptyList();
        }
        List<Long> campaignIds = campaigns.stream().map(OzonPromotionCampaign::getCampaignId).toList();
        Map<Long, List<OzonPromotionCampaignStatistics>> statsByCampaign =
                ozonPromotionCampaignStatisticsRepository
                        .findByCampaign_CampaignIdInAndDateBetween(campaignIds, from, to)
                        .stream()
                        .collect(Collectors.groupingBy(s -> s.getCampaign().getCampaignId()));
        Map<Long, Integer> articlesCountByCampaign = new HashMap<>();
        for (Object[] row : ozonCampaignArticleRepository.countByCampaignIdIn(campaignIds)) {
            articlesCountByCampaign.put((Long) row[0], ((Number) row[1]).intValue());
        }

        return campaigns.stream()
                .map(c -> buildOzonCampaignDto(
                        c,
                        statsByCampaign.getOrDefault(c.getCampaignId(), Collections.emptyList()),
                        articlesCountByCampaign.getOrDefault(c.getCampaignId(), 0)))
                .collect(Collectors.toList());
    }

    private CampaignDto buildOzonCampaignDto(
            OzonPromotionCampaign campaign,
            List<OzonPromotionCampaignStatistics> stats,
            Integer articlesCount
    ) {
        boolean running = "CAMPAIGN_STATE_RUNNING".equals(campaign.getState());
        String type = buildOzonCampaignTypeLabel(campaign.getAdvObjectType(), campaign.getPaymentType());

        int views = 0;
        int clicks = 0;
        BigDecimal spend = BigDecimal.ZERO;
        int orders = 0;
        for (OzonPromotionCampaignStatistics s : stats) {
            if (s.getViews() != null) {
                views += s.getViews();
            }
            if (s.getClicks() != null) {
                clicks += s.getClicks();
            }
            if (s.getSpend() != null) {
                spend = spend.add(s.getSpend());
            }
            if (s.getOrders() != null) {
                orders += s.getOrders();
            }
        }
        BigDecimal ctr = null;
        BigDecimal cpc = null;
        if (views > 0) {
            ctr = BigDecimal.valueOf(clicks)
                    .multiply(BigDecimal.valueOf(100))
                    .divide(BigDecimal.valueOf(views), 2, RoundingMode.HALF_UP);
        }
        if (clicks > 0) {
            cpc = spend.divide(BigDecimal.valueOf(clicks), 2, RoundingMode.HALF_UP);
        }

        return CampaignDto.builder()
                .id(campaign.getCampaignId())
                .name(campaign.getTitle())
                .type(type)
                .status(running ? 9 : 4)
                .statusName(formatOzonCampaignState(campaign.getState()))
                .createdAt(campaign.getOzonCreatedAt())
                .updatedAt(campaign.getOzonUpdatedAt() != null ? campaign.getOzonUpdatedAt() : campaign.getSyncedAt())
                .views(views)
                .clicks(clicks)
                .ctr(ctr)
                .cpc(cpc)
                .costs(spend)
                .orders(orders)
                .articlesCount(articlesCount)
                .build();
    }

    private static String buildOzonCampaignTypeLabel(String advObjectType, String paymentType) {
        if (advObjectType == null && paymentType == null) {
            return null;
        }
        if (advObjectType == null) {
            return paymentType;
        }
        if (paymentType == null) {
            return advObjectType;
        }
        return advObjectType + " / " + paymentType;
    }

    private static String formatOzonCampaignState(String state) {
        if (state == null || state.isBlank()) {
            return "неизвестно";
        }
        return switch (state) {
            case "CAMPAIGN_STATE_RUNNING" -> "активна";
            case "CAMPAIGN_STATE_STOPPED" -> "остановлена";
            case "CAMPAIGN_STATE_INACTIVE" -> "неактивна";
            case "CAMPAIGN_STATE_FINISHED" -> "завершена";
            case "CAMPAIGN_STATE_PLANNED" -> "запланирована";
            default -> state.replace("CAMPAIGN_STATE_", "").toLowerCase(Locale.ROOT);
        };
    }

    /**
     * Детали рекламной кампании (комбо): название, статус, список артикулов с фото и названием.
     * Сначала поиск по кабинету; если кабинет в контексте не задан — по advertId с проверкой владельца кабинета.
     */
    @Transactional(readOnly = true)
    public CampaignDetailDto getCampaignDetail(Long campaignId, Long cabinetId, Long sellerId) {
        WbPromotionCampaign campaign = resolveCampaignForDetail(campaignId, cabinetId, sellerId);
        Long cabinetIdForArticles = cabinetId;
        if (campaign != null && campaign.getCabinet() != null) {
            cabinetIdForArticles = campaign.getCabinet().getId();
        }
        if (campaign == null) {
            return null;
        }
        if (cabinetIdForArticles == null) {
            return null;
        }
        final Long finalCabinetId = cabinetIdForArticles;
        List<WbCampaignArticle> campaignArticles = campaignArticleRepository.findByCampaignId(campaign.getAdvertId());
        List<Long> nmIds = campaignArticles.stream()
                .map(WbCampaignArticle::getNmId)
                .distinct()
                .collect(Collectors.toList());
        boolean itemRatingSupported = isItemRatingSupported(null, finalCabinetId);
        List<ArticleSummaryDto> articles = nmIds.stream()
                .map(nmId -> productCardRepository.findByNmIdAndCabinet_Id(nmId, finalCabinetId)
                        .map(card -> mapToArticleSummary(card, itemRatingSupported))
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
                .campaignGoal(campaignGoalService.findGoalText(finalCabinetId, campaign.getAdvertId()).orElse(null))
                .build();
    }

    /**
     * Агрегированная статистика по поисковым кластерам кампании за период.
     *
     * @param nmId если задан — только по артикулу; иначе по всем артикулам комбо
     * @param search подстрока для фильтра по названию кластера
     */
    @Transactional(readOnly = true)
    public NormQueryClustersResponseDto getCampaignNormQueryClusters(
            Long campaignId,
            Long cabinetId,
            Long sellerId,
            LocalDate dateFrom,
            LocalDate dateTo,
            Long nmId,
            String search,
            String sortBy,
            String sortDir,
            Integer page,
            Integer size
    ) {
        WbPromotionCampaign campaign = resolveCampaignForDetail(campaignId, cabinetId, sellerId);
        if (campaign == null) {
            return null;
        }
        LocalDate from = dateFrom != null ? dateFrom : LocalDate.now().minusDays(DEFAULT_DAILY_DATA_SPAN_DAYS - 1);
        LocalDate to = dateTo != null ? dateTo : LocalDate.now();
        if (from.isAfter(to)) {
            LocalDate tmp = from;
            from = to;
            to = tmp;
        }
        return normQueryStatisticsService.getAggregatedClustersPage(
                campaign.getAdvertId(),
                from,
                to,
                nmId,
                search,
                NormQueryClusterSortField.fromParam(sortBy),
                Sort.Direction.fromOptionalString(sortDir).orElse(Sort.Direction.DESC),
                page != null ? page : 0,
                size != null ? size : 20
        );
    }

    private WbPromotionCampaign resolveCampaignForDetail(Long campaignId, Long cabinetId, Long sellerId) {
        WbPromotionCampaign campaign = null;
        if (cabinetId != null) {
            campaign = campaignRepository.findByAdvertIdAndCabinet_Id(campaignId, cabinetId).orElse(null);
        }
        if (campaign == null && sellerId != null) {
            campaign = campaignRepository.findById(campaignId).orElse(null);
            if (campaign != null
                    && (campaign.getCabinet() == null
                    || campaign.getCabinet().getUser() == null
                    || !campaign.getCabinet().getUser().getId().equals(sellerId))) {
                campaign = null;
            }
        }
        return campaign;
    }

    /**
     * Детализация остатков FBO по размерам на складе WB.
     *
     * @param nmId          артикул
     * @param warehouseName название склада, если {@code warehouseId} не задан
     * @param warehouseId   ID склада WB (опционально)
     * @param cabinetId     кабинет (опционально, сужает выборку)
     * @return остатки по размерам, включая нули
     */
    public List<StockSizeDto> getStockSizes(Long nmId, String warehouseName, Long warehouseId, Long cabinetId) {
        Long resolvedWarehouseId = warehouseId;
        if (resolvedWarehouseId == null) {
            resolvedWarehouseId = warehouseRepository.findAll().stream()
                    .filter(w -> w.getName().equals(warehouseName))
                    .map(w -> Long.valueOf(w.getId()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Склад не найден: " + warehouseName));
        }

        List<WbProductStock> stocks = cabinetId != null
                ? stockRepository.findByNmIdAndWarehouseIdAndCabinet_Id(nmId, resolvedWarehouseId, cabinetId)
                : stockRepository.findByNmIdAndWarehouseId(nmId, resolvedWarehouseId);

        List<WbProductBarcode> barcodes = cabinetId != null
                ? barcodeRepository.findByNmIdAndCabinet_Id(nmId, cabinetId)
                : barcodeRepository.findByNmId(nmId);
        Map<String, WbProductBarcode> barcodeMap = barcodes.stream()
                .collect(Collectors.toMap(
                        WbProductBarcode::getBarcode,
                        b -> b,
                        (existing, replacement) -> existing
                ));

        Map<String, StockSizeAggregate> allSizes = seedSizesFromBarcodes(barcodeMap.values());
        for (WbProductStock stock : stocks) {
            WbProductBarcode barcode = barcodeMap.get(stock.getBarcode());
            if (barcode == null) {
                continue;
            }
            addSizeAmount(allSizes, barcode, stock.getAmount());
        }
        return toStockSizeDtos(allSizes);
    }

    /**
     * Детализация остатков FBS по размерам на складе продавца.
     *
     * @param nmId          артикул
     * @param warehouseName название склада продавца, если {@code warehouseId} не задан
     * @param warehouseId   ID склада продавца (опционально)
     * @param cabinetId     кабинет
     * @return остатки по размерам, включая нули
     */
    public List<StockSizeDto> getFbsStockSizes(Long nmId, String warehouseName, Long warehouseId, Long cabinetId) {
        if (cabinetId == null) {
            return Collections.emptyList();
        }
        Long resolvedWarehouseId = warehouseId;
        if (resolvedWarehouseId == null) {
            resolvedWarehouseId = sellerWarehouseRepository.findByCabinet_Id(cabinetId).stream()
                    .filter(w -> w.getName().equals(warehouseName))
                    .map(WbSellerWarehouse::getWarehouseId)
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Склад продавца не найден: " + warehouseName));
        }

        List<WbProductFbsStock> stocks = fbsStockRepository.findByNmIdAndCabinet_IdAndWarehouseId(
                nmId, cabinetId, resolvedWarehouseId);
        List<WbProductBarcode> barcodes = barcodeRepository.findByNmIdAndCabinet_Id(nmId, cabinetId);
        Map<Long, WbProductBarcode> barcodeByChrtId = new HashMap<>();
        for (WbProductBarcode barcode : barcodes) {
            if (barcode.getChrtId() != null) {
                barcodeByChrtId.putIfAbsent(barcode.getChrtId(), barcode);
            }
        }

        Map<String, StockSizeAggregate> allSizes = seedSizesFromBarcodes(barcodes);
        for (WbProductFbsStock stock : stocks) {
            WbProductBarcode barcode = barcodeByChrtId.get(stock.getChrtId());
            if (barcode == null) {
                continue;
            }
            addSizeAmount(allSizes, barcode, stock.getAmount());
        }
        return toStockSizeDtos(allSizes);
    }

    private Map<String, StockSizeAggregate> seedSizesFromBarcodes(Iterable<WbProductBarcode> barcodes) {
        Map<String, StockSizeAggregate> allSizes = new HashMap<>();
        for (WbProductBarcode barcode : barcodes) {
            String sizeKey = sizeKey(barcode);
            if (!allSizes.containsKey(sizeKey)) {
                allSizes.put(sizeKey, new StockSizeAggregate(barcode.getTechSize(), barcode.getWbSize(), 0));
            }
        }
        return allSizes;
    }

    private void addSizeAmount(Map<String, StockSizeAggregate> allSizes, WbProductBarcode barcode, int amount) {
        String sizeKey = sizeKey(barcode);
        StockSizeAggregate agg = allSizes.get(sizeKey);
        if (agg != null) {
            agg.setAmount(agg.getAmount() + amount);
        } else {
            allSizes.put(sizeKey, new StockSizeAggregate(barcode.getTechSize(), barcode.getWbSize(), amount));
        }
    }

    private static String sizeKey(WbProductBarcode barcode) {
        if (barcode.getWbSize() != null && !barcode.getWbSize().isEmpty()) {
            return barcode.getWbSize();
        }
        return barcode.getTechSize() != null ? barcode.getTechSize() : "Неизвестно";
    }

    private List<StockSizeDto> toStockSizeDtos(Map<String, StockSizeAggregate> allSizes) {
        return allSizes.values().stream()
                .map(agg -> StockSizeDto.builder()
                        .techSize(agg.getTechSize())
                        .wbSize(agg.getWbSize())
                        .amount(agg.getAmount())
                        .build())
                .sorted((a, b) -> {
                    if (a.getWbSize() != null && b.getWbSize() != null) {
                        try {
                            return Integer.compare(Integer.parseInt(a.getWbSize()), Integer.parseInt(b.getWbSize()));
                        } catch (NumberFormatException e) {
                            return a.getWbSize().compareTo(b.getWbSize());
                        }
                    }
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

    @Transactional(readOnly = true)
    public WbProductCard findCardBySeller(Long nmId, Long sellerId) {
        return findCardBySeller(nmId, sellerId, null);
    }

    @Transactional(readOnly = true)
    public WbProductCard findCardBySeller(Long nmId, Long sellerId, Long cabinetId) {
        WbProductCard card = (cabinetId != null
                ? productCardRepository.findByNmIdAndCabinetIdWithCabinetAndUser(nmId, cabinetId)
                : productCardRepository.findByNmIdWithCabinetAndUser(nmId))
                .orElseThrow(() -> new UserException("Артикул не найден: " + nmId, HttpStatus.NOT_FOUND));

        if (card.getCabinet() == null
                || card.getCabinet().getUser() == null
                || !card.getCabinet().getUser().getId().equals(sellerId)) {
            throw new UserException("Артикул не принадлежит продавцу", HttpStatus.FORBIDDEN);
        }

        return card;
    }

    @Transactional
    public void updateArticlePriority(User seller, Long cabinetId, Long nmId, boolean isPriority) {
        WbProductCard card = findCardBySeller(nmId, seller.getId(), cabinetId);
        card.setIsPriority(isPriority);
        productCardRepository.save(card);
    }

    private List<ArticleSummaryDto> mapToArticleSummaries(List<WbProductCard> cards, boolean itemRatingSupported) {
        return cards.stream()
                .map(card -> mapToArticleSummary(card, itemRatingSupported))
                .collect(Collectors.toList());
    }

    private boolean cardHasAnyPhoto(WbProductCard card) {
        return (card.getPhotoTm() != null && !card.getPhotoTm().isBlank())
                || (card.getPhotoC246x328() != null && !card.getPhotoC246x328().isBlank());
    }

    private ArticleSummaryDto mapToArticleSummary(WbProductCard card, boolean itemRatingSupported) {
        return ArticleSummaryDto.builder()
                .nmId(card.getNmId())
                .marketplaceType(MarketplaceType.WB)
                .title(card.getTitle())
                .brand(card.getBrand())
                .subjectName(card.getSubjectName())
                .photoTm(card.getPhotoTm())
                .photoC246x328(card.getPhotoC246x328())
                .vendorCode(card.getVendorCode())
                .rating(itemRatingSupported ? ArticleRatingUtils.toDisplayRating(card.getRating()) : null)
                .isPriority(Boolean.TRUE.equals(card.getIsPriority()))
                .wbCreatedAt(card.getWbCreatedAt())
                .build();
    }

    /**
     * Страница артикула Ozon: заказы/выручка по периодам и по дням, цена, остатки FBO/FBS.
     */
    private ArticleResponseDto getOzonArticle(
            Long cabinetId,
            Long productId,
            List<PeriodDto> periods,
            LocalDate campaignDateFrom,
            LocalDate campaignDateTo,
            LocalDate dailyDataDateFrom,
            LocalDate dailyDataDateTo
    ) {
        OzonProductCard card = ozonProductCardRepository.findByCabinet_IdAndProductId(cabinetId, productId)
                .orElseThrow(() -> new UserException("Товар Ozon не найден", HttpStatus.NOT_FOUND));

        List<PeriodDto> sortedPeriods = periods != null && !periods.isEmpty()
                ? sortPeriodsByDateFrom(periods)
                : List.of();

        List<DailyDataDto> dailyData = getOzonDailyData(cabinetId, productId, dailyDataDateFrom, dailyDataDateTo);
        List<MetricDto> metrics = buildOzonArticleMetrics(cabinetId, productId, sortedPeriods);
        OzonStockSummary stockSummary = loadOzonStockSummary(cabinetId, productId);

        LocalDate rkFrom = campaignDateFrom != null ? campaignDateFrom : dailyDataDateFrom;
        LocalDate rkTo = campaignDateTo != null ? campaignDateTo : dailyDataDateTo;
        List<CampaignDto> campaigns = getOzonCampaignsForProduct(cabinetId, productId, card.getSku(), rkFrom, rkTo);

        List<StockDto> fboStocks = List.of(StockDto.builder()
                .warehouseName("FBO")
                .amount(stockSummary.fbo())
                .build());
        List<StockDto> fbsStocks = List.of(StockDto.builder()
                .warehouseName("FBS")
                .amount(stockSummary.fbs())
                .build());

        LocalDateTime lastStocksUpdateTriggeredAt = cabinetService.findById(cabinetId)
                .map(Cabinet::getLastStocksUpdateRequestedAt)
                .orElse(null);

        return ArticleResponseDto.builder()
                .article(mapOzonToArticleDetail(card))
                .periods(sortedPeriods)
                .metrics(metrics)
                .dailyData(dailyData)
                .campaigns(campaigns)
                .inWbPromotion(false)
                .wbPromotionNames(List.of())
                .wbPromotionTypes(List.of())
                .stocks(fboStocks)
                .fbsStocks(fbsStocks)
                .bundleProducts(List.of())
                .lastStocksUpdateTriggeredAt(lastStocksUpdateTriggeredAt)
                .articleGoal(null)
                .build();
    }

    /**
     * РК Ozon, в которых участвует товар (по product_id или sku).
     * Метрики — агрегаты по кампании за период (не доля SKU).
     */
    private List<CampaignDto> getOzonCampaignsForProduct(
            Long cabinetId,
            Long productId,
            Long sku,
            LocalDate dateFrom,
            LocalDate dateTo
    ) {
        List<OzonCampaignArticle> links = new ArrayList<>();
        if (productId != null) {
            links.addAll(ozonCampaignArticleRepository.findByProductId(productId));
        }
        if (sku != null) {
            for (OzonCampaignArticle link : ozonCampaignArticleRepository.findBySkuFetched(sku)) {
                boolean already = links.stream()
                        .anyMatch(l -> Objects.equals(l.getCampaignId(), link.getCampaignId()));
                if (!already) {
                    links.add(link);
                }
            }
        }
        if (links.isEmpty()) {
            return Collections.emptyList();
        }

        List<OzonPromotionCampaign> campaigns = links.stream()
                .map(OzonCampaignArticle::getCampaign)
                .filter(Objects::nonNull)
                .filter(c -> c.getCabinet() != null && Objects.equals(c.getCabinet().getId(), cabinetId))
                .filter(c -> !"CAMPAIGN_STATE_FINISHED".equals(c.getState()))
                .distinct()
                .collect(Collectors.toList());
        if (campaigns.isEmpty()) {
            return Collections.emptyList();
        }

        boolean withMetrics = dateFrom != null && dateTo != null;
        Map<Long, List<OzonPromotionCampaignStatistics>> statsByCampaign = Map.of();
        if (withMetrics) {
            AnalyticsDateRange range = resolveAnalyticsDateRange(dateFrom, dateTo);
            List<Long> campaignIds = campaigns.stream().map(OzonPromotionCampaign::getCampaignId).toList();
            statsByCampaign = ozonPromotionCampaignStatisticsRepository
                    .findByCampaign_CampaignIdInAndDateBetween(campaignIds, range.startDate(), range.endDate())
                    .stream()
                    .collect(Collectors.groupingBy(s -> s.getCampaign().getCampaignId()));
        }
        Map<Long, Integer> articlesCountByCampaign = new HashMap<>();
        List<Long> campaignIds = campaigns.stream().map(OzonPromotionCampaign::getCampaignId).toList();
        for (Object[] row : ozonCampaignArticleRepository.countByCampaignIdIn(campaignIds)) {
            articlesCountByCampaign.put((Long) row[0], ((Number) row[1]).intValue());
        }

        final Map<Long, List<OzonPromotionCampaignStatistics>> statsFinal = statsByCampaign;
        return campaigns.stream()
                .map(c -> buildOzonCampaignDto(
                        c,
                        withMetrics
                                ? statsFinal.getOrDefault(c.getCampaignId(), Collections.emptyList())
                                : Collections.emptyList(),
                        articlesCountByCampaign.getOrDefault(c.getCampaignId(), 0)))
                .collect(Collectors.toList());
    }

    private List<DailyDataDto> getOzonDailyData(
            Long cabinetId,
            Long productId,
            LocalDate dailyDataDateFrom,
            LocalDate dailyDataDateTo
    ) {
        AnalyticsDateRange range = resolveAnalyticsDateRange(dailyDataDateFrom, dailyDataDateTo);
        LocalDate startDate = range.startDate();
        LocalDate endDate = range.endDate();

        Map<LocalDate, OzonProductCardAnalytics> analyticsByDate = ozonProductCardAnalyticsRepository
                .findByCabinet_IdAndProductIdAndDateBetween(cabinetId, productId, startDate, endDate)
                .stream()
                .collect(Collectors.toMap(OzonProductCardAnalytics::getDate, row -> row, (a, b) -> a));

        Map<LocalDate, OzonProductPriceHistory> priceByDate = ozonProductPriceHistoryRepository
                .findByCabinet_IdAndProductIdAndDateBetween(cabinetId, productId, startDate, endDate)
                .stream()
                .collect(Collectors.toMap(OzonProductPriceHistory::getDate, row -> row, (a, b) -> a));

        List<DailyDataDto> result = new ArrayList<>();
        LocalDate current = startDate;
        while (!current.isAfter(endDate)) {
            DailyDataDto.DailyDataDtoBuilder builder = DailyDataDto.builder().date(current);
            OzonProductCardAnalytics analytics = analyticsByDate.get(current);
            if (analytics != null) {
                builder.orders(analytics.getOrderedUnits())
                        .ordersAmount(analytics.getRevenue());
            }
            OzonProductPriceHistory price = priceByDate.get(current);
            if (price != null) {
                builder.priceBeforeDiscount(price.getOldPrice())
                        .priceWithDiscount(price.getPrice());
            }
            result.add(builder.build());
            current = current.plusDays(1);
        }
        return result;
    }

    private List<MetricDto> buildOzonArticleMetrics(Long cabinetId, Long productId, List<PeriodDto> periods) {
        if (periods.isEmpty()) {
            return List.of();
        }
        LocalDate minFrom = periods.stream().map(PeriodDto::getDateFrom).min(LocalDate::compareTo).orElseThrow();
        LocalDate maxTo = periods.stream().map(PeriodDto::getDateTo).max(LocalDate::compareTo).orElseThrow();
        List<OzonProductCardAnalytics> rows = ozonProductCardAnalyticsRepository
                .findByCabinet_IdAndProductIdAndDateBetween(cabinetId, productId, minFrom, maxTo);

        List<MetricDto> metrics = new ArrayList<>();
        for (String metricName : List.of(MetricNames.ORDERS, MetricNames.ORDERS_AMOUNT)) {
            List<PeriodMetricValueDto> periodValues = new ArrayList<>();
            for (PeriodDto period : periods) {
                OzonPeriodTotals totals = sumOzonAnalyticsForPeriod(rows, period);
                Object value = MetricNames.ORDERS.equals(metricName)
                        ? totals.orderedUnits()
                        : totals.revenue();
                BigDecimal changePercent = calculateOzonChangePercent(metricName, period, periods, rows);
                periodValues.add(PeriodMetricValueDto.builder()
                        .periodId(period.getId())
                        .value(value)
                        .changePercent(changePercent)
                        .build());
            }
            metrics.add(MetricDto.builder()
                    .metricName(metricName)
                    .metricNameRu(MetricNames.getRussianName(metricName))
                    .category("funnel")
                    .periods(periodValues)
                    .build());
        }
        return metrics;
    }

    private OzonStockSummary loadOzonStockSummary(Long cabinetId, Long productId) {
        int fbo = 0;
        int fbs = 0;
        for (OzonProductStock stock : ozonProductStockRepository.findByCabinet_IdAndProductId(cabinetId, productId)) {
            int present = stock.getPresent() != null ? stock.getPresent() : 0;
            String type = stock.getStockType() != null ? stock.getStockType().trim().toLowerCase() : "";
            if ("fbo".equals(type)) {
                fbo += present;
            } else if ("fbs".equals(type)) {
                fbs += present;
            }
        }
        return new OzonStockSummary(fbo, fbs);
    }

    private ArticleDetailDto mapOzonToArticleDetail(OzonProductCard card) {
        return ArticleDetailDto.builder()
                .nmId(card.getProductId())
                .title(card.getTitle())
                .vendorCode(card.getOfferId())
                .photoTm(card.getPhotoUrl())
                .productUrl("")
                .build();
    }

    private record OzonStockSummary(int fbo, int fbs) {
    }

    private ArticleDetailDto mapToArticleDetail(WbProductCard card, boolean itemRatingSupported) {
        return ArticleDetailDto.builder()
                .nmId(card.getNmId())
                .imtId(card.getImtId())
                .title(card.getTitle())
                .brand(card.getBrand())
                .subjectName(card.getSubjectName())
                .vendorCode(card.getVendorCode())
                .photoTm(card.getPhotoTm())
                .photoC246x328(card.getPhotoC246x328())
                .rating(itemRatingSupported ? ArticleRatingUtils.toDisplayRating(card.getRating()) : null)
                .productUrl("https://www.wildberries.ru/catalog/" + card.getNmId() + "/detail.aspx")
                .createdAt(card.getWbCreatedAt())
                .updatedAt(card.getUpdatedAt())
                .build();
    }

    /**
     * Item-rating WB недоступен для кабинетов с базовым токеном.
     */
    private boolean isItemRatingSupported(Long sellerId, Long cabinetId) {
        if (cabinetId != null) {
            return cabinetService.findById(cabinetId)
                    .map(c -> CabinetTokenType.effective(c.getTokenType()).supportsItemRating())
                    .orElse(false);
        }
        if (sellerId == null) {
            return false;
        }
        return cabinetService.findDefaultByUserId(sellerId)
                .map(c -> CabinetTokenType.effective(c.getTokenType()).supportsItemRating())
                .orElse(false);
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
