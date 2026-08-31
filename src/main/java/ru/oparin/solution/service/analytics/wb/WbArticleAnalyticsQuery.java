package ru.oparin.solution.service.analytics.wb;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.oparin.solution.dto.analytics.*;
import ru.oparin.solution.model.*;
import ru.oparin.solution.repository.*;
import ru.oparin.solution.service.CabinetService;
import ru.oparin.solution.service.WbArticleGoalService;
import ru.oparin.solution.service.analytics.*;
import ru.oparin.solution.service.analytics.AnalyticsDateRangeResolver.AnalyticsDateRange;
import ru.oparin.solution.util.ArticleRatingUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Карточка артикула WB: метрики, dailyData, бандлы, акции.
 */
@Service
@RequiredArgsConstructor
public class WbArticleAnalyticsQuery {

    private final WbArticleCatalogQuery articleCatalogQuery;
    private final WbCampaignAnalyticsQuery campaignAnalyticsQuery;
    private final AnalyticsStockQuery stockQuery;
    private final WbProductCardRepository productCardRepository;
    private final WbProductCardAnalyticsRepository analyticsRepository;
    private final WbProductPriceHistoryRepository priceHistoryRepository;
    private final WbPromotionCampaignRepository campaignRepository;
    private final WbPromotionCampaignStatisticsRepository campaignStatisticsRepository;
    private final WbPromotionParticipationRepository promotionParticipationRepository;
    private final MetricValueCalculator metricValueCalculator;
    private final WbCampaignStatisticsAggregator campaignStatisticsAggregator;
    private final CabinetService cabinetService;
    private final WbArticleGoalService articleGoalService;

    /**
     * Детальная страница артикула WB.
     */
    @Transactional(readOnly = true)
    public ArticleResponseDto getArticle(
            User seller,
            Long cabinetId,
            Long nmId,
            List<PeriodDto> periods,
            LocalDate campaignDateFrom,
            LocalDate campaignDateTo,
            LocalDate dailyDataDateFrom,
            LocalDate dailyDataDateTo,
            Long dailyDataCampaignAdvertId
    ) {
        WbProductCard card = articleCatalogQuery.findCardBySeller(nmId, seller.getId(), cabinetId);
        Long cardCabinetId = card.getCabinet() != null ? card.getCabinet().getId() : null;

        List<DailyDataDto> dailyData = getDailyData(
                nmId, cardCabinetId, dailyDataDateFrom, dailyDataDateTo, dailyDataCampaignAdvertId);
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
        boolean itemRatingSupported = articleCatalogQuery.isItemRatingSupported(
                seller.getId(), cabinetId != null ? cabinetId : cardCabinetId);
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
                .campaigns(campaignAnalyticsQuery.getCampaignsForArticle(
                        nmId, cardCabinetId, campaignDateFrom, campaignDateTo))
                .inWbPromotion(!wbPromotionNames.isEmpty())
                .wbPromotionNames(wbPromotionNames)
                .wbPromotionTypes(wbPromotionTypes)
                .stocks(stockQuery.getStocks(nmId, cardCabinetId))
                .fbsStocks(stockQuery.getFbsStocks(nmId, cardCabinetId))
                .bundleProducts(bundleProducts)
                .lastStocksUpdateTriggeredAt(lastStocksUpdateTriggeredAt)
                .articleGoal(articleGoal)
                .build();
    }

    private List<ArticleSummaryDto> getBundleProducts(
            WbProductCard card,
            Long cardCabinetId,
            boolean itemRatingSupported
    ) {
        if (card.getImtId() == null) {
            return Collections.emptyList();
        }
        Long cabinetId = cardCabinetId != null ? cardCabinetId
                : (card.getCabinet() != null ? card.getCabinet().getId() : null);
        if (cabinetId == null) {
            return Collections.emptyList();
        }
        List<WbProductCard> sameImt = productCardRepository.findByImtIdAndCabinet_Id(card.getImtId(), cabinetId);
        return sameImt.stream()
                .filter(c -> !c.getNmId().equals(card.getNmId()))
                .map(c -> articleCatalogQuery.mapToArticleSummary(c, itemRatingSupported))
                .collect(Collectors.toList());
    }

    private List<MetricDto> calculateAllMetrics(
            WbProductCard card,
            List<PeriodDto> periods,
            Long sellerId,
            Long cabinetId
    ) {
        Map<PeriodDto, WbCampaignStatisticsAggregator.AdvertisingStats> advertisingStatsCache =
                preloadAdvertisingStats(sellerId, cabinetId, periods);

        List<MetricDto> metrics = new ArrayList<>();
        for (String metricName : MetricNames.getAllMetrics()) {
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

            metrics.add(MetricDto.builder()
                    .metricName(metricName)
                    .metricNameRu(MetricNames.getRussianName(metricName))
                    .category(AnalyticsPercentChange.metricCategory(metricName))
                    .periods(periodValues)
                    .build());
        }
        return metrics;
    }

    private Map<PeriodDto, WbCampaignStatisticsAggregator.AdvertisingStats> preloadAdvertisingStats(
            Long sellerId,
            Long cabinetId,
            List<PeriodDto> periods
    ) {
        List<Long> campaignIds = campaignAnalyticsQuery.getCampaignIdsForCabinet(sellerId, cabinetId);
        Map<PeriodDto, WbCampaignStatisticsAggregator.AdvertisingStats> cache = new HashMap<>();
        for (PeriodDto period : periods) {
            cache.put(period, campaignStatisticsAggregator.aggregateStats(campaignIds, period));
        }
        return cache;
    }

    private List<DailyDataDto> getDailyData(
            Long nmId,
            Long cabinetId,
            LocalDate from,
            LocalDate to,
            Long campaignAdvertId
    ) {
        AnalyticsDateRange range = AnalyticsDateRangeResolver.resolve(from, to);
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

        Map<LocalDate, AdvertisingDailyStats> advertisingByDate = advertisingData.stream()
                .collect(Collectors.groupingBy(
                        WbPromotionCampaignStatistics::getDate,
                        Collectors.collectingAndThen(
                                Collectors.toList(),
                                this::aggregateAdvertisingStats
                        )
                ));

        Map<LocalDate, WbProductPriceHistory> priceByDate = priceData.stream()
                .collect(Collectors.groupingBy(
                        WbProductPriceHistory::getDate,
                        Collectors.collectingAndThen(
                                Collectors.toList(),
                                WbArticleAnalyticsQuery::pickRepresentativePriceRow
                        )
                ));

        Map<LocalDate, WbProductCardAnalytics> funnelByDate = funnelData.stream()
                .collect(Collectors.toMap(
                        WbProductCardAnalytics::getDate,
                        a -> a,
                        (a1, a2) -> a1
                ));

        List<LocalDate> allDates = new ArrayList<>();
        LocalDate currentDate = startDate;
        while (!currentDate.isAfter(endDate)) {
            allDates.add(currentDate);
            currentDate = currentDate.plusDays(1);
        }

        return allDates.stream()
                .map(date -> {
                    WbProductCardAnalytics funnel = funnelByDate.get(date);
                    AdvertisingDailyStats advertising = advertisingByDate.get(date);

                    DailyDataDto.DailyDataDtoBuilder builder = DailyDataDto.builder().date(date);

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
                        if (funnel != null && funnel.getOrders() != null && funnel.getOrders() > 0
                                && advertising.costs != null) {
                            builder.cpo(MathUtils.divideSafely(advertising.costs, BigDecimal.valueOf(funnel.getOrders())));
                        } else {
                            builder.cpo(advertising.cpo);
                        }
                        if (funnel != null && funnel.getOrdersSum() != null
                                && funnel.getOrdersSum().compareTo(BigDecimal.ZERO) > 0
                                && advertising.costs != null) {
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

                        if (price.getSppDiscount() != null && price.getClubDiscountedPrice() != null) {
                            BigDecimal sppPercent = BigDecimal.valueOf(price.getSppDiscount());
                            builder.sppPercent(sppPercent);

                            BigDecimal sppAmount = price.getClubDiscountedPrice()
                                    .multiply(sppPercent)
                                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
                            builder.sppAmount(sppAmount);
                            builder.priceWithSpp(price.getClubDiscountedPrice().subtract(sppAmount));
                        }
                    }

                    return builder.build();
                })
                .collect(Collectors.toList());
    }

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
            if (stat.getViews() != null) {
                views += stat.getViews();
            }
            if (stat.getClicks() != null) {
                clicks += stat.getClicks();
            }
            if (stat.getSum() != null) {
                sum = sum.add(stat.getSum());
            }
            if (stat.getOrders() != null) {
                orders += stat.getOrders();
            }
            if (stat.getOrdersSum() != null) {
                ordersSum = ordersSum.add(stat.getOrdersSum());
            }
        }

        return new AdvertisingDailyStats(
                views,
                clicks,
                sum,
                MathUtils.divideSafely(sum, BigDecimal.valueOf(clicks)),
                MathUtils.calculatePercentage(clicks, views),
                MathUtils.divideSafely(sum, BigDecimal.valueOf(orders)),
                MathUtils.calculatePercentage(sum, ordersSum)
        );
    }

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

    private static BigDecimal calculateCartConversion(Integer openCard, Integer addToCart) {
        if (openCard == null || addToCart == null || openCard == 0) {
            return null;
        }
        return MathUtils.calculatePercentage(addToCart, openCard);
    }

    private static BigDecimal calculateOrderConversion(Integer addToCart, Integer orders) {
        if (addToCart == null || orders == null || addToCart == 0) {
            return null;
        }
        return MathUtils.calculatePercentage(orders, addToCart);
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

    private record AdvertisingDailyStats(
            Integer views,
            Integer clicks,
            BigDecimal costs,
            BigDecimal cpc,
            BigDecimal ctr,
            BigDecimal cpo,
            BigDecimal drr
    ) {
    }
}
