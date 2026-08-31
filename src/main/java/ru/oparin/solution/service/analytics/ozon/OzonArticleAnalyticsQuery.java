package ru.oparin.solution.service.analytics.ozon;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.oparin.solution.dto.analytics.*;
import ru.oparin.solution.exception.UserException;
import ru.oparin.solution.model.*;
import ru.oparin.solution.repository.OzonProductCardAnalyticsRepository;
import ru.oparin.solution.repository.OzonProductPriceHistoryRepository;
import ru.oparin.solution.repository.OzonProductStockRepository;
import ru.oparin.solution.repository.OzonPromotionCampaignProductStatisticsRepository;
import ru.oparin.solution.service.CabinetService;
import ru.oparin.solution.service.analytics.AnalyticsDateRangeResolver;
import ru.oparin.solution.service.analytics.AnalyticsDateRangeResolver.AnalyticsDateRange;
import ru.oparin.solution.service.analytics.AnalyticsPercentChange;
import ru.oparin.solution.service.analytics.MetricNames;
import ru.oparin.solution.service.analytics.OzonSummaryMetricsCalculator;
import ru.oparin.solution.util.ArticleRatingUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Карточка товара Ozon: метрики, dailyData, остатки, РК.
 */
@Service
@RequiredArgsConstructor
public class OzonArticleAnalyticsQuery {

    private final OzonArticleCatalogQuery articleCatalogQuery;
    private final OzonCampaignAnalyticsQuery campaignAnalyticsQuery;
    private final OzonProductCardAnalyticsRepository ozonProductCardAnalyticsRepository;
    private final OzonProductPriceHistoryRepository ozonProductPriceHistoryRepository;
    private final OzonProductStockRepository ozonProductStockRepository;
    private final OzonPromotionCampaignProductStatisticsRepository ozonPromotionCampaignProductStatisticsRepository;
    private final OzonSummaryMetricsCalculator ozonSummaryMetricsCalculator;
    private final CabinetService cabinetService;

    /**
     * Страница товара Ozon.
     */
    @Transactional(readOnly = true)
    public ArticleResponseDto getArticle(
            Long cabinetId,
            Long productId,
            List<PeriodDto> periods,
            LocalDate campaignDateFrom,
            LocalDate campaignDateTo,
            LocalDate dailyDataDateFrom,
            LocalDate dailyDataDateTo
    ) {
        OzonProductCard card = articleCatalogQuery.findCard(cabinetId, productId)
                .orElseThrow(() -> new UserException("Товар Ozon не найден", HttpStatus.NOT_FOUND));

        List<PeriodDto> sortedPeriods = periods != null && !periods.isEmpty()
                ? AnalyticsPercentChange.sortPeriodsByDateFrom(periods)
                : List.of();

        List<DailyDataDto> dailyData = getDailyData(cabinetId, productId, dailyDataDateFrom, dailyDataDateTo);
        List<MetricDto> metrics = buildArticleMetrics(card, cabinetId, sortedPeriods);
        OzonStockSummary stockSummary = loadStockSummary(cabinetId, productId);

        LocalDate rkFrom = campaignDateFrom != null ? campaignDateFrom : dailyDataDateFrom;
        LocalDate rkTo = campaignDateTo != null ? campaignDateTo : dailyDataDateTo;
        List<CampaignDto> campaigns = campaignAnalyticsQuery.getCampaignsForProduct(
                cabinetId, productId, card.getSku(), rkFrom, rkTo);

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
                .article(mapToArticleDetail(card))
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

    private List<DailyDataDto> getDailyData(
            Long cabinetId,
            Long productId,
            LocalDate dailyDataDateFrom,
            LocalDate dailyDataDateTo
    ) {
        AnalyticsDateRange range = AnalyticsDateRangeResolver.resolve(dailyDataDateFrom, dailyDataDateTo);
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

    private List<MetricDto> buildArticleMetrics(OzonProductCard card, Long cabinetId, List<PeriodDto> periods) {
        if (periods.isEmpty()) {
            return List.of();
        }
        Long productId = card.getProductId();
        LocalDate minFrom = periods.stream().map(PeriodDto::getDateFrom).min(LocalDate::compareTo).orElseThrow();
        LocalDate maxTo = periods.stream().map(PeriodDto::getDateTo).max(LocalDate::compareTo).orElseThrow();
        List<OzonProductCardAnalytics> rows = ozonProductCardAnalyticsRepository
                .findByCabinet_IdAndProductIdAndDateBetween(cabinetId, productId, minFrom, maxTo);
        List<OzonPromotionCampaignProductStatistics> adRows =
                ozonPromotionCampaignProductStatisticsRepository.findByCampaign_Cabinet_IdAndDateBetween(
                        cabinetId, minFrom, maxTo);

        List<MetricDto> metrics = new ArrayList<>();
        for (String metricName : MetricNames.getAllMetrics()) {
            List<PeriodMetricValueDto> periodValues = new ArrayList<>();
            for (PeriodDto period : periods) {
                Object value = ozonSummaryMetricsCalculator.calculateArticleValue(
                        metricName, card, period, rows, adRows);
                PeriodDto previousPeriod = AnalyticsPercentChange.findPreviousPeriodByDateOrder(period, periods);
                Object previousValue = previousPeriod == null
                        ? null
                        : ozonSummaryMetricsCalculator.calculateArticleValue(
                        metricName, card, previousPeriod, rows, adRows);
                periodValues.add(PeriodMetricValueDto.builder()
                        .periodId(period.getId())
                        .value(value)
                        .changePercent(AnalyticsPercentChange.between(metricName, value, previousValue))
                        .build());
            }
            metrics.add(MetricDto.builder()
                    .metricName(metricName)
                    .metricNameRu(MetricNames.getRussianName(metricName))
                    .category(AnalyticsPercentChange.metricCategory(metricName))
                    .periods(periodValues)
                    .build());
        }
        return metrics;
    }

    private OzonStockSummary loadStockSummary(Long cabinetId, Long productId) {
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

    private ArticleDetailDto mapToArticleDetail(OzonProductCard card) {
        return ArticleDetailDto.builder()
                .nmId(card.getProductId())
                .title(card.getTitle())
                .vendorCode(card.getOfferId())
                .photoTm(card.getPhotoUrl())
                .rating(ArticleRatingUtils.toDisplayRating(card.getContentRating()))
                .productUrl(buildOzonProductUrl(card.getProductId()))
                .build();
    }

    private static String buildOzonProductUrl(Long productId) {
        if (productId == null) {
            return "";
        }
        return "https://www.ozon.ru/product/" + productId + "/";
    }

    private record OzonStockSummary(int fbo, int fbs) {
    }
}
