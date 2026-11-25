package ru.oparin.solution.service.analytics.wb;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.oparin.solution.dto.analytics.*;
import ru.oparin.solution.model.*;
import ru.oparin.solution.repository.*;
import ru.oparin.solution.service.analytics.AnalyticsPercentChange;
import ru.oparin.solution.service.analytics.CatalogPageHelper;
import ru.oparin.solution.service.analytics.MathUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Дашборды WB: дневная динамика, остатки FBO/FBS, товарные рейтинги.
 */
@Service
@RequiredArgsConstructor
public class WbDashboardQuery {

    private static final int RANKING_LIMIT = 5;
    private static final Set<String> FUNNEL_METRICS = Set.of(
            "orders", "ordersAmount", "transitions", "cart"
    );
    private static final Set<String> AD_METRICS = Set.of("costs", "clicks", "views");

    private final WbArticleCatalogQuery articleCatalogQuery;
    private final WbProductCardAnalyticsRepository analyticsRepository;
    private final WbPromotionCampaignStatisticsRepository campaignStatisticsRepository;
    private final WbProductStockRepository stockRepository;
    private final WbProductFbsStockRepository fbsStockRepository;
    private final WbProductBarcodeRepository barcodeRepository;

    /**
     * Дневной ряд выбранной метрики по отфильтрованным артикулам.
     */
    @Transactional(readOnly = true)
    public DashboardDailyResponseDto getDaily(
            User seller,
            Long cabinetId,
            DashboardDailyRequestDto request
    ) {
        String metric = normalizeMetric(request.getMetric());
        List<PeriodDto> periods = AnalyticsPercentChange.sortPeriodsByDateFrom(request.getPeriods());
        AnalyticsPercentChange.validatePeriods(periods);

        List<WbProductCard> cards = resolveFilteredCards(seller, cabinetId, request);
        if (cards.isEmpty()) {
            return DashboardDailyResponseDto.builder()
                    .metric(metric)
                    .points(emptyDailyPoints(periods))
                    .build();
        }

        LocalDate dateFrom = periods.stream().map(PeriodDto::getDateFrom).min(LocalDate::compareTo).orElseThrow();
        LocalDate dateTo = periods.stream().map(PeriodDto::getDateTo).max(LocalDate::compareTo).orElseThrow();
        List<Long> nmIds = cards.stream().map(WbProductCard::getNmId).filter(Objects::nonNull).toList();

        Map<LocalDate, BigDecimal> byDate = FUNNEL_METRICS.contains(metric)
                ? aggregateFunnelDaily(cabinetId, nmIds, dateFrom, dateTo, metric)
                : aggregateAdsDaily(cabinetId, seller.getId(), nmIds, dateFrom, dateTo, metric);

        List<DashboardDailyPointDto> points = new ArrayList<>();
        for (LocalDate day = dateFrom; !day.isAfter(dateTo); day = day.plusDays(1)) {
            points.add(DashboardDailyPointDto.builder()
                    .date(day)
                    .value(byDate.getOrDefault(day, BigDecimal.ZERO))
                    .build());
        }
        return DashboardDailyResponseDto.builder()
                .metric(metric)
                .points(points)
                .build();
    }

    /**
     * Актуальный срез остатков FBO/FBS (периоды продаж не влияют).
     */
    @Transactional(readOnly = true)
    public DashboardStocksResponseDto getStocks(
            User seller,
            Long cabinetId,
            DashboardStocksRequestDto request
    ) {
        List<WbProductCard> cards = resolveFilteredCards(seller, cabinetId, request);
        boolean itemRatingSupported = articleCatalogQuery.isItemRatingSupported(seller.getId(), cabinetId);
        List<ArticleSummaryDto> articles = articleCatalogQuery.mapToArticleSummaries(cards, itemRatingSupported);

        if (cards.isEmpty() || cabinetId == null) {
            return DashboardStocksResponseDto.builder()
                    .total(0)
                    .fbo(0)
                    .fbs(0)
                    .fboSharePercent(BigDecimal.ZERO)
                    .fbsSharePercent(BigDecimal.ZERO)
                    .bySize(List.of())
                    .updatedAt(null)
                    .articles(articles)
                    .build();
        }

        List<Long> nmIds = cards.stream().map(WbProductCard::getNmId).filter(Objects::nonNull).toList();
        Long focusNmId = request.getNmId();
        if (focusNmId != null) {
            if (!nmIds.contains(focusNmId)) {
                return DashboardStocksResponseDto.builder()
                        .total(0)
                        .fbo(0)
                        .fbs(0)
                        .fboSharePercent(BigDecimal.ZERO)
                        .fbsSharePercent(BigDecimal.ZERO)
                        .bySize(List.of())
                        .updatedAt(null)
                        .articles(articles)
                        .build();
            }
            nmIds = List.of(focusNmId);
        }

        String fulfillment = normalizeFulfillment(request.getFulfillment());
        List<WbProductStock> fboStocks = stockRepository.findByCabinet_IdAndNmIdIn(cabinetId, nmIds);
        List<WbProductFbsStock> fbsStocks = fbsStockRepository.findByCabinet_IdAndNmIdIn(cabinetId, nmIds);
        List<WbProductBarcode> barcodes = barcodeRepository.findByCabinet_IdAndNmIdIn(cabinetId, nmIds);

        int fboTotal = fboStocks.stream().mapToInt(s -> MathUtils.getValueOrZero(s.getAmount())).sum();
        int fbsTotal = fbsStocks.stream().mapToInt(s -> MathUtils.getValueOrZero(s.getAmount())).sum();

        LocalDateTime updatedAt = maxUpdatedAt(fboStocks, fbsStocks);

        Map<String, int[]> bySize = new LinkedHashMap<>();
        seedSizes(bySize, barcodes);

        Map<String, WbProductBarcode> barcodeByCode = barcodes.stream()
                .collect(Collectors.toMap(WbProductBarcode::getBarcode, b -> b, (a, b) -> a));
        Map<Long, WbProductBarcode> barcodeByChrtId = new HashMap<>();
        for (WbProductBarcode barcode : barcodes) {
            if (barcode.getChrtId() != null) {
                barcodeByChrtId.putIfAbsent(barcode.getChrtId(), barcode);
            }
        }

        if (!"FBS".equals(fulfillment)) {
            for (WbProductStock stock : fboStocks) {
                WbProductBarcode barcode = barcodeByCode.get(stock.getBarcode());
                String size = sizeLabel(barcode);
                int[] amounts = bySize.computeIfAbsent(size, k -> new int[2]);
                amounts[0] += MathUtils.getValueOrZero(stock.getAmount());
            }
        }
        if (!"FBO".equals(fulfillment)) {
            for (WbProductFbsStock stock : fbsStocks) {
                WbProductBarcode barcode = barcodeByChrtId.get(stock.getChrtId());
                String size = sizeLabel(barcode);
                int[] amounts = bySize.computeIfAbsent(size, k -> new int[2]);
                amounts[1] += MathUtils.getValueOrZero(stock.getAmount());
            }
        }

        int displayFbo = "FBS".equals(fulfillment) ? 0 : fboTotal;
        int displayFbs = "FBO".equals(fulfillment) ? 0 : fbsTotal;
        int total = displayFbo + displayFbs;

        List<DashboardStockSizeDto> sizeDtos = bySize.entrySet().stream()
                .map(entry -> {
                    int fbo = "FBS".equals(fulfillment) ? 0 : entry.getValue()[0];
                    int fbs = "FBO".equals(fulfillment) ? 0 : entry.getValue()[1];
                    return DashboardStockSizeDto.builder()
                            .size(entry.getKey())
                            .fbo(fbo)
                            .fbs(fbs)
                            .total(fbo + fbs)
                            .build();
                })
                .sorted(this::compareSizeDto)
                .collect(Collectors.toList());

        return DashboardStocksResponseDto.builder()
                .total(total)
                .fbo(displayFbo)
                .fbs(displayFbs)
                .fboSharePercent(sharePercent(displayFbo, total))
                .fbsSharePercent(sharePercent(displayFbs, total))
                .bySize(sizeDtos)
                .updatedAt(updatedAt)
                .articles(articles)
                .build();
    }

    /**
     * Топ-5 по заказам / росту / падению между двумя последними периодами.
     */
    @Transactional(readOnly = true)
    public DashboardRankingsResponseDto getRankings(
            User seller,
            Long cabinetId,
            DashboardRankingsRequestDto request
    ) {
        List<PeriodDto> periods = AnalyticsPercentChange.sortPeriodsByDateFrom(request.getPeriods());
        AnalyticsPercentChange.validatePeriods(periods);
        List<WbProductCard> cards = resolveFilteredCards(seller, cabinetId, request);
        if (cards.isEmpty()) {
            return emptyRankings();
        }

        PeriodDto lastPeriod = periods.get(periods.size() - 1);
        PeriodDto prevPeriod = periods.size() >= 2 ? periods.get(periods.size() - 2) : null;

        List<Long> nmIds = cards.stream().map(WbProductCard::getNmId).filter(Objects::nonNull).toList();
        Map<Long, WbProductCard> cardByNmId = cards.stream()
                .filter(c -> c.getNmId() != null)
                .collect(Collectors.toMap(WbProductCard::getNmId, c -> c, (a, b) -> a));

        Map<Long, OrdersAgg> lastOrders = aggregateOrdersByNmId(cabinetId, nmIds, lastPeriod);
        Map<Long, OrdersAgg> prevOrders = prevPeriod != null
                ? aggregateOrdersByNmId(cabinetId, nmIds, prevPeriod)
                : Collections.emptyMap();

        List<DashboardRankingItemDto> topByOrders = lastOrders.entrySet().stream()
                .filter(e -> e.getValue().orders > 0)
                .sorted((a, b) -> {
                    int cmp = Integer.compare(b.getValue().orders, a.getValue().orders);
                    if (cmp != 0) {
                        return cmp;
                    }
                    return b.getValue().ordersAmount.compareTo(a.getValue().ordersAmount);
                })
                .limit(RANKING_LIMIT)
                .map(e -> toRankingItem(cardByNmId.get(e.getKey()), e.getValue(), null, 0))
                .collect(Collectors.toList());
        assignRanks(topByOrders);

        List<GrowthCandidate> growthCandidates = new ArrayList<>();
        if (prevPeriod != null) {
            for (Long nmId : nmIds) {
                OrdersAgg last = lastOrders.getOrDefault(nmId, OrdersAgg.ZERO);
                OrdersAgg prev = prevOrders.getOrDefault(nmId, OrdersAgg.ZERO);
                if (prev.orders <= 0) {
                    continue;
                }
                BigDecimal change = percentChange(last.orders, prev.orders);
                if (change == null) {
                    continue;
                }
                growthCandidates.add(new GrowthCandidate(nmId, last, change));
            }
        }

        List<DashboardRankingItemDto> topGrowth = growthCandidates.stream()
                .filter(c -> c.changePercent.compareTo(BigDecimal.ZERO) > 0)
                .sorted((a, b) -> b.changePercent.compareTo(a.changePercent))
                .limit(RANKING_LIMIT)
                .map(c -> toRankingItem(cardByNmId.get(c.nmId), c.last, c.changePercent, 0))
                .collect(Collectors.toList());
        assignRanks(topGrowth);

        List<DashboardRankingItemDto> topDecline = growthCandidates.stream()
                .filter(c -> c.changePercent.compareTo(BigDecimal.ZERO) < 0)
                .sorted(Comparator.comparing(c -> c.changePercent))
                .limit(RANKING_LIMIT)
                .map(c -> toRankingItem(cardByNmId.get(c.nmId), c.last, c.changePercent, 0))
                .collect(Collectors.toList());
        assignRanks(topDecline);

        return DashboardRankingsResponseDto.builder()
                .topByOrders(topByOrders)
                .topGrowth(topGrowth)
                .topDecline(topDecline)
                .build();
    }

    private List<WbProductCard> resolveFilteredCards(
            User seller,
            Long cabinetId,
            DashboardDailyRequestDto request
    ) {
        return resolveFilteredCards(
                seller,
                cabinetId,
                request.getExcludedNmIds(),
                request.getIncludedNmIds(),
                request.getFilterToNone(),
                request.getSearch(),
                request.getOnlyWithPhoto(),
                request.getOnlyPriority(),
                request.getOnlyInAdvertising()
        );
    }

    private List<WbProductCard> resolveFilteredCards(
            User seller,
            Long cabinetId,
            DashboardStocksRequestDto request
    ) {
        return resolveFilteredCards(
                seller,
                cabinetId,
                request.getExcludedNmIds(),
                request.getIncludedNmIds(),
                request.getFilterToNone(),
                request.getSearch(),
                request.getOnlyWithPhoto(),
                request.getOnlyPriority(),
                request.getOnlyInAdvertising()
        );
    }

    private List<WbProductCard> resolveFilteredCards(
            User seller,
            Long cabinetId,
            DashboardRankingsRequestDto request
    ) {
        return resolveFilteredCards(
                seller,
                cabinetId,
                request.getExcludedNmIds(),
                request.getIncludedNmIds(),
                request.getFilterToNone(),
                request.getSearch(),
                request.getOnlyWithPhoto(),
                request.getOnlyPriority(),
                request.getOnlyInAdvertising()
        );
    }

    private List<WbProductCard> resolveFilteredCards(
            User seller,
            Long cabinetId,
            List<Long> excludedNmIds,
            List<Long> includedNmIds,
            Boolean filterToNone,
            String search,
            Boolean onlyWithPhoto,
            Boolean onlyPriority,
            Boolean onlyInAdvertising
    ) {
        List<WbProductCard> visible = articleCatalogQuery.applyCatalogFilters(
                articleCatalogQuery.getVisibleCards(seller.getId(), cabinetId, excludedNmIds),
                seller.getId(),
                cabinetId,
                onlyWithPhoto,
                onlyPriority,
                onlyInAdvertising
        );
        List<WbProductCard> filtered = CatalogPageHelper.applyInclusionFilter(
                visible, filterToNone, includedNmIds, WbProductCard::getNmId);
        if (search != null && !search.isBlank()) {
            filtered = articleCatalogQuery.filterCardsBySearch(filtered, search.trim());
        }
        return filtered;
    }

    private Map<LocalDate, BigDecimal> aggregateFunnelDaily(
            Long cabinetId,
            List<Long> nmIds,
            LocalDate dateFrom,
            LocalDate dateTo,
            String metric
    ) {
        List<WbProductCardAnalytics> rows = cabinetId != null
                ? analyticsRepository.findByCabinet_IdAndProductCardNmIdInAndDateBetween(
                cabinetId, nmIds, dateFrom, dateTo)
                : analyticsRepository.findByProductCardNmIdInAndDateBetween(nmIds, dateFrom, dateTo);

        Map<LocalDate, BigDecimal> byDate = new HashMap<>();
        for (WbProductCardAnalytics row : rows) {
            if (row.getDate() == null) {
                continue;
            }
            BigDecimal value = switch (metric) {
                case "orders" -> BigDecimal.valueOf(MathUtils.getValueOrZero(row.getOrders()));
                case "ordersAmount" -> MathUtils.getValueOrZero(row.getOrdersSum());
                case "transitions" -> BigDecimal.valueOf(MathUtils.getValueOrZero(row.getOpenCard()));
                case "cart" -> BigDecimal.valueOf(MathUtils.getValueOrZero(row.getAddToCart()));
                default -> BigDecimal.ZERO;
            };
            byDate.merge(row.getDate(), value, BigDecimal::add);
        }
        return byDate;
    }

    private Map<LocalDate, BigDecimal> aggregateAdsDaily(
            Long cabinetId,
            Long sellerId,
            List<Long> nmIds,
            LocalDate dateFrom,
            LocalDate dateTo,
            String metric
    ) {
        List<WbPromotionCampaignStatistics> rows;
        if (cabinetId != null) {
            rows = campaignStatisticsRepository.findByCabinetIdAndNmIdInAndDateBetween(
                    cabinetId, nmIds, dateFrom, dateTo);
        } else {
            rows = new ArrayList<>();
            for (Long nmId : nmIds) {
                rows.addAll(campaignStatisticsRepository.findBySellerIdAndNmIdAndDateBetween(
                        sellerId, nmId, dateFrom, dateTo));
            }
        }

        Map<LocalDate, BigDecimal> byDate = new HashMap<>();
        for (WbPromotionCampaignStatistics row : rows) {
            if (row.getDate() == null) {
                continue;
            }
            BigDecimal value = switch (metric) {
                case "costs" -> row.getSum() != null ? row.getSum() : BigDecimal.ZERO;
                case "clicks" -> BigDecimal.valueOf(MathUtils.getValueOrZero(row.getClicks()));
                case "views" -> BigDecimal.valueOf(MathUtils.getValueOrZero(row.getViews()));
                default -> BigDecimal.ZERO;
            };
            byDate.merge(row.getDate(), value, BigDecimal::add);
        }
        return byDate;
    }

    private Map<Long, OrdersAgg> aggregateOrdersByNmId(Long cabinetId, List<Long> nmIds, PeriodDto period) {
        List<WbProductCardAnalytics> rows = cabinetId != null
                ? analyticsRepository.findByCabinet_IdAndProductCardNmIdInAndDateBetween(
                cabinetId, nmIds, period.getDateFrom(), period.getDateTo())
                : analyticsRepository.findByProductCardNmIdInAndDateBetween(
                nmIds, period.getDateFrom(), period.getDateTo());

        Map<Long, OrdersAgg> result = new HashMap<>();
        for (WbProductCardAnalytics row : rows) {
            Long nmId = row.getProductCard() != null ? row.getProductCard().getNmId() : null;
            if (nmId == null) {
                continue;
            }
            OrdersAgg current = result.getOrDefault(nmId, OrdersAgg.ZERO);
            result.put(nmId, new OrdersAgg(
                    current.orders + MathUtils.getValueOrZero(row.getOrders()),
                    current.ordersAmount.add(MathUtils.getValueOrZero(row.getOrdersSum()))
            ));
        }
        return result;
    }

    private DashboardRankingItemDto toRankingItem(
            WbProductCard card,
            OrdersAgg orders,
            BigDecimal changePercent,
            int rank
    ) {
        return DashboardRankingItemDto.builder()
                .rank(rank)
                .nmId(card != null ? card.getNmId() : null)
                .photoTm(card != null ? card.getPhotoTm() : null)
                .title(card != null ? card.getTitle() : null)
                .vendorCode(card != null ? card.getVendorCode() : null)
                .orders(orders.orders)
                .ordersAmount(orders.ordersAmount)
                .changePercent(changePercent)
                .build();
    }

    private void assignRanks(List<DashboardRankingItemDto> items) {
        for (int i = 0; i < items.size(); i++) {
            items.get(i).setRank(i + 1);
        }
    }

    private static BigDecimal percentChange(int current, int previous) {
        if (previous <= 0) {
            return null;
        }
        return BigDecimal.valueOf(current - previous)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(previous), 2, RoundingMode.HALF_UP);
    }

    private static String normalizeMetric(String metric) {
        if (metric == null || metric.isBlank()) {
            throw new IllegalArgumentException("Метрика обязательна");
        }
        String normalized = metric.trim();
        if (!FUNNEL_METRICS.contains(normalized) && !AD_METRICS.contains(normalized)) {
            throw new IllegalArgumentException("Неизвестная метрика дашборда: " + metric);
        }
        return normalized;
    }

    private static String normalizeFulfillment(String fulfillment) {
        if (fulfillment == null || fulfillment.isBlank()) {
            return "ALL";
        }
        String value = fulfillment.trim().toUpperCase(Locale.ROOT);
        if (!Set.of("ALL", "FBO", "FBS").contains(value)) {
            throw new IllegalArgumentException("Неизвестный тип фулфилмента: " + fulfillment);
        }
        return value;
    }

    private static List<DashboardDailyPointDto> emptyDailyPoints(List<PeriodDto> periods) {
        if (periods == null || periods.isEmpty()) {
            return List.of();
        }
        LocalDate dateFrom = periods.stream().map(PeriodDto::getDateFrom).min(LocalDate::compareTo).orElseThrow();
        LocalDate dateTo = periods.stream().map(PeriodDto::getDateTo).max(LocalDate::compareTo).orElseThrow();
        List<DashboardDailyPointDto> points = new ArrayList<>();
        for (LocalDate day = dateFrom; !day.isAfter(dateTo); day = day.plusDays(1)) {
            points.add(DashboardDailyPointDto.builder().date(day).value(BigDecimal.ZERO).build());
        }
        return points;
    }

    private static DashboardRankingsResponseDto emptyRankings() {
        return DashboardRankingsResponseDto.builder()
                .topByOrders(List.of())
                .topGrowth(List.of())
                .topDecline(List.of())
                .build();
    }

    private static void seedSizes(Map<String, int[]> bySize, List<WbProductBarcode> barcodes) {
        for (WbProductBarcode barcode : barcodes) {
            bySize.putIfAbsent(sizeLabel(barcode), new int[2]);
        }
    }

    private static String sizeLabel(WbProductBarcode barcode) {
        if (barcode == null) {
            return "Неизвестно";
        }
        if (barcode.getWbSize() != null && !barcode.getWbSize().isEmpty()) {
            return barcode.getWbSize();
        }
        return barcode.getTechSize() != null ? barcode.getTechSize() : "Неизвестно";
    }

    private int compareSizeDto(DashboardStockSizeDto a, DashboardStockSizeDto b) {
        try {
            return Integer.compare(Integer.parseInt(a.getSize()), Integer.parseInt(b.getSize()));
        } catch (NumberFormatException ignored) {
            return a.getSize().compareTo(b.getSize());
        }
    }

    private static BigDecimal sharePercent(int part, int total) {
        if (total <= 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(part)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(total), 1, RoundingMode.HALF_UP);
    }

    private static LocalDateTime maxUpdatedAt(List<WbProductStock> fbo, List<WbProductFbsStock> fbs) {
        LocalDateTime max = null;
        for (WbProductStock stock : fbo) {
            if (stock.getUpdatedAt() != null && (max == null || stock.getUpdatedAt().isAfter(max))) {
                max = stock.getUpdatedAt();
            }
        }
        for (WbProductFbsStock stock : fbs) {
            if (stock.getUpdatedAt() != null && (max == null || stock.getUpdatedAt().isAfter(max))) {
                max = stock.getUpdatedAt();
            }
        }
        return max;
    }

    private record OrdersAgg(int orders, BigDecimal ordersAmount) {
        private static final OrdersAgg ZERO = new OrdersAgg(0, BigDecimal.ZERO);
    }

    private record GrowthCandidate(Long nmId, OrdersAgg last, BigDecimal changePercent) {
    }
}
