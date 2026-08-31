package ru.oparin.solution.service.analytics.ozon;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.oparin.solution.dto.analytics.ArticleSummaryDto;
import ru.oparin.solution.dto.analytics.ArticleSummarySortField;
import ru.oparin.solution.model.*;
import ru.oparin.solution.repository.*;
import ru.oparin.solution.util.ArticleRatingUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Каталог товаров Ozon: список, фильтры, маппинг в ArticleSummaryDto.
 */
@Service
@RequiredArgsConstructor
public class OzonArticleCatalogQuery {

    private final OzonProductCardRepository ozonProductCardRepository;
    private final OzonProductPriceHistoryRepository ozonProductPriceHistoryRepository;
    private final OzonProductStockRepository ozonProductStockRepository;
    private final OzonProductCardAnalyticsRepository ozonProductCardAnalyticsRepository;
    private final OzonCampaignArticleRepository ozonCampaignArticleRepository;

    /**
     * Список товаров кабинета для попапа фильтра (с ценой, остатками и заказами за 14 дней).
     */
    @Transactional(readOnly = true)
    public List<ArticleSummaryDto> getArticleList(
            Long cabinetId,
            Boolean onlyWithPhoto,
            Boolean onlyInAdvertising
    ) {
        List<OzonProductCard> cards = getVisibleCards(cabinetId, null, onlyWithPhoto);
        if (Boolean.TRUE.equals(onlyInAdvertising)) {
            cards = applyOnlyInAdvertising(cards, cabinetId);
        }
        if (cards.isEmpty()) {
            return List.of();
        }

        Map<Long, OzonProductPriceHistory> priceByProductId = loadLatestPrices(cabinetId);
        Map<Long, int[]> stocksByProductId = loadStockTotals(cabinetId);
        Map<Long, OzonAnalyticsTotals> analyticsByProductId = loadAnalyticsTotals(cabinetId);

        return cards.stream()
                .map(card -> mapToArticleSummary(
                        card,
                        priceByProductId.get(card.getProductId()),
                        stocksByProductId.get(card.getProductId()),
                        analyticsByProductId.get(card.getProductId())
                ))
                .toList();
    }

    /**
     * Товары кабинета с фильтрами excluded/фото.
     */
    public List<OzonProductCard> getVisibleCards(
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

    /**
     * Оставляет товары, привязанные к незавершённым РК.
     */
    public List<OzonProductCard> applyOnlyInAdvertising(List<OzonProductCard> cards, Long cabinetId) {
        Set<Long> advertised = new HashSet<>(ozonCampaignArticleRepository.findActiveProductIdsByCabinetId(cabinetId));
        return cards.stream()
                .filter(card -> card.getProductId() != null && advertised.contains(card.getProductId()))
                .collect(Collectors.toList());
    }

    /**
     * Маппинг страницы сводной (без агрегатов заказов за 14 дней).
     */
    public List<ArticleSummaryDto> mapToArticleSummaries(List<OzonProductCard> cards, Long cabinetId) {
        if (cards.isEmpty()) {
            return List.of();
        }
        Map<Long, OzonProductPriceHistory> priceByProductId = loadLatestPrices(cabinetId);
        Map<Long, int[]> stocksByProductId = loadStockTotals(cabinetId);
        return cards.stream()
                .map(card -> mapToArticleSummary(
                        card,
                        priceByProductId.get(card.getProductId()),
                        stocksByProductId.get(card.getProductId()),
                        null
                ))
                .toList();
    }

    /**
     * Поиск по названию, offerId или productId.
     */
    public List<OzonProductCard> filterCardsBySearch(List<OzonProductCard> cards, String search) {
        String lower = search.toLowerCase();
        return cards.stream()
                .filter(card ->
                        (card.getTitle() != null && card.getTitle().toLowerCase().contains(lower))
                                || (card.getOfferId() != null && card.getOfferId().toLowerCase().contains(lower))
                                || (card.getProductId() != null && String.valueOf(card.getProductId()).contains(lower))
                )
                .collect(Collectors.toList());
    }

    /**
     * Сортировка списка товаров Ozon.
     */
    public void sortProductCards(
            List<OzonProductCard> cards,
            ArticleSummarySortField sortBy,
            Sort.Direction sortDir
    ) {
        ArticleSummarySortField effectiveSortBy = sortBy != null ? sortBy : ArticleSummarySortField.WB_CREATED_AT;
        Comparator<OzonProductCard> comparator = switch (effectiveSortBy) {
            case WB_CREATED_AT -> Comparator.comparing(
                    OzonProductCard::getCreatedAt,
                    Comparator.nullsLast(Comparator.naturalOrder())
            );
        };
        if (sortDir == Sort.Direction.DESC) {
            comparator = comparator.reversed();
        }
        cards.sort(comparator);
    }

    /**
     * Карточка товара по кабинету и product_id.
     */
    public Optional<OzonProductCard> findCard(Long cabinetId, Long productId) {
        return ozonProductCardRepository.findByCabinet_IdAndProductId(cabinetId, productId);
    }

    /**
     * Карточки по SKU в кабинете.
     */
    public List<OzonProductCard> findByCabinetAndSku(Long cabinetId, Long sku) {
        return ozonProductCardRepository.findByCabinet_IdAndSkuIn(cabinetId, List.of(sku));
    }

    private ArticleSummaryDto mapToArticleSummary(
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
        builder.rating(ArticleRatingUtils.toDisplayRating(card.getContentRating()));
        if (card.getCreatedAt() != null) {
            builder.wbCreatedAt(card.getCreatedAt());
        }
        return builder.build();
    }

    private Map<Long, OzonProductPriceHistory> loadLatestPrices(Long cabinetId) {
        Optional<LocalDate> maxDate = ozonProductPriceHistoryRepository.findMaxDateByCabinetId(cabinetId);
        if (maxDate.isEmpty()) {
            return Map.of();
        }
        return ozonProductPriceHistoryRepository.findByCabinet_IdAndDate(cabinetId, maxDate.get()).stream()
                .collect(Collectors.toMap(OzonProductPriceHistory::getProductId, p -> p, (a, b) -> a));
    }

    private Map<Long, int[]> loadStockTotals(Long cabinetId) {
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

    private Map<Long, OzonAnalyticsTotals> loadAnalyticsTotals(Long cabinetId) {
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

    private static final class OzonAnalyticsTotals {
        private int orderedUnits;
        private BigDecimal revenue = BigDecimal.ZERO;
    }
}
