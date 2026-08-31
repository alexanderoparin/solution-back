package ru.oparin.solution.service.analytics.ozon;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.oparin.solution.dto.analytics.*;
import ru.oparin.solution.model.*;
import ru.oparin.solution.repository.OzonCampaignArticleRepository;
import ru.oparin.solution.repository.OzonPromotionCampaignProductStatisticsRepository;
import ru.oparin.solution.repository.OzonPromotionCampaignRepository;
import ru.oparin.solution.repository.OzonPromotionCampaignStatisticsRepository;
import ru.oparin.solution.service.OzonPromotionCampaignSearchPhraseStatisticsService;
import ru.oparin.solution.service.analytics.AnalyticsDateRangeResolver;
import ru.oparin.solution.service.analytics.AnalyticsDateRangeResolver.AnalyticsDateRange;
import ru.oparin.solution.service.ozon.OzonCampaignTypeLabels;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Чтение рекламных кампаний Ozon: список, деталь, поисковые фразы, РК на карточке товара.
 */
@Service
@RequiredArgsConstructor
public class OzonCampaignAnalyticsQuery {

    private final OzonPromotionCampaignRepository ozonPromotionCampaignRepository;
    private final OzonPromotionCampaignStatisticsRepository ozonPromotionCampaignStatisticsRepository;
    private final OzonPromotionCampaignProductStatisticsRepository ozonPromotionCampaignProductStatisticsRepository;
    private final OzonCampaignArticleRepository ozonCampaignArticleRepository;
    private final OzonPromotionCampaignSearchPhraseStatisticsService ozonSearchPhraseStatisticsService;
    private final OzonArticleCatalogQuery articleCatalogQuery;

    /**
     * Незавершённые РК кабинета с агрегацией дневной статистики.
     */
    @Transactional(readOnly = true)
    public List<CampaignDto> listByCabinet(Long cabinetId, LocalDate dateFrom, LocalDate dateTo) {
        if (cabinetId == null) {
            return Collections.emptyList();
        }
        AnalyticsDateRange range = AnalyticsDateRangeResolver.resolve(dateFrom, dateTo);
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
        Map<Long, Integer> cartByCampaign = new HashMap<>();
        for (OzonPromotionCampaignProductStatistics ps : ozonPromotionCampaignProductStatisticsRepository
                .findByCampaign_CampaignIdInAndDateBetween(campaignIds, from, to)) {
            if (ps.getToCart() == null || ps.getToCart() <= 0) {
                continue;
            }
            Long campaignId = ps.getCampaign().getCampaignId();
            cartByCampaign.merge(campaignId, ps.getToCart(), Integer::sum);
        }
        Map<Long, Integer> articlesCountByCampaign = loadArticlesCount(campaignIds);

        return campaigns.stream()
                .map(c -> buildCampaignDto(
                        c,
                        statsByCampaign.getOrDefault(c.getCampaignId(), Collections.emptyList()),
                        articlesCountByCampaign.getOrDefault(c.getCampaignId(), 0),
                        cartByCampaign.get(c.getCampaignId())))
                .collect(Collectors.toList());
    }

    /**
     * РК товара для страницы карточки (по product_id или sku).
     */
    @Transactional(readOnly = true)
    public List<CampaignDto> getCampaignsForProduct(
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

        boolean withMetrics = dateFrom != null && dateTo != null && sku != null;
        Map<Long, List<OzonPromotionCampaignProductStatistics>> productStatsByCampaign = Map.of();
        Map<Long, List<OzonPromotionCampaignStatistics>> campaignStatsByCampaign = Map.of();
        if (withMetrics) {
            AnalyticsDateRange range = AnalyticsDateRangeResolver.resolve(dateFrom, dateTo);
            List<Long> campaignIds = campaigns.stream().map(OzonPromotionCampaign::getCampaignId).toList();
            List<OzonPromotionCampaignProductStatistics> productStats =
                    ozonPromotionCampaignProductStatisticsRepository.findByCampaign_CampaignIdInAndSkuAndDateBetween(
                            campaignIds, sku, range.startDate(), range.endDate());
            if (!productStats.isEmpty()) {
                productStatsByCampaign = productStats.stream()
                        .collect(Collectors.groupingBy(s -> s.getCampaign().getCampaignId()));
            } else {
                campaignStatsByCampaign = ozonPromotionCampaignStatisticsRepository
                        .findByCampaign_CampaignIdInAndDateBetween(campaignIds, range.startDate(), range.endDate())
                        .stream()
                        .collect(Collectors.groupingBy(s -> s.getCampaign().getCampaignId()));
            }
        }
        List<Long> campaignIds = campaigns.stream().map(OzonPromotionCampaign::getCampaignId).toList();
        Map<Long, Integer> articlesCountByCampaign = loadArticlesCount(campaignIds);

        final Map<Long, List<OzonPromotionCampaignProductStatistics>> productStatsFinal = productStatsByCampaign;
        final Map<Long, List<OzonPromotionCampaignStatistics>> campaignStatsFinal = campaignStatsByCampaign;
        return campaigns.stream()
                .map(c -> {
                    List<OzonPromotionCampaignProductStatistics> ps =
                            productStatsFinal.getOrDefault(c.getCampaignId(), Collections.emptyList());
                    if (!ps.isEmpty()) {
                        return buildCampaignDtoFromProductStats(
                                c, ps, articlesCountByCampaign.getOrDefault(c.getCampaignId(), 0));
                    }
                    return buildCampaignDto(
                            c,
                            withMetrics
                                    ? campaignStatsFinal.getOrDefault(c.getCampaignId(), Collections.emptyList())
                                    : Collections.emptyList(),
                            articlesCountByCampaign.getOrDefault(c.getCampaignId(), 0),
                            null);
                })
                .collect(Collectors.toList());
    }

    /**
     * Детали РК Ozon или {@code null}, если кампания не найдена.
     */
    @Transactional(readOnly = true)
    public CampaignDetailDto getCampaignDetail(Long campaignId, Long cabinetId, Long sellerId) {
        OzonPromotionCampaign campaign = resolveCampaignForDetail(campaignId, cabinetId, sellerId);
        if (campaign == null) {
            return null;
        }
        Long cabinetIdForArticles = campaign.getCabinet() != null ? campaign.getCabinet().getId() : cabinetId;
        if (cabinetIdForArticles == null) {
            return null;
        }
        List<OzonCampaignArticle> links = ozonCampaignArticleRepository.findByCampaignIdIn(List.of(campaign.getCampaignId()));
        List<ArticleSummaryDto> articles = links.stream()
                .map(link -> mapCampaignArticle(link, cabinetIdForArticles))
                .distinct()
                .collect(Collectors.toList());
        return CampaignDetailDto.builder()
                .id(campaign.getCampaignId())
                .name(campaign.getTitle())
                .status(null)
                .statusName(mapCampaignStateLabel(campaign.getState()))
                .articlesCount(articles.size())
                .articles(articles)
                .createdAt(campaign.getOzonCreatedAt())
                .campaignGoal(null)
                .build();
    }

    /**
     * Поисковые фразы кампании Ozon.
     */
    @Transactional(readOnly = true)
    public NormQueryClustersResponseDto getSearchPhraseClusters(
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
        OzonPromotionCampaign ozonCampaign = resolveCampaignForDetail(campaignId, cabinetId, sellerId);
        if (ozonCampaign == null) {
            return null;
        }
        return ozonSearchPhraseStatisticsService.getAggregatedClustersPage(
                ozonCampaign.getCampaignId(),
                dateFrom,
                dateTo,
                nmId,
                search,
                NormQueryClusterSortField.fromParam(sortBy),
                Sort.Direction.fromOptionalString(sortDir).orElse(Sort.Direction.DESC),
                page != null ? page : 0,
                size != null ? size : 20
        );
    }

    /**
     * Поиск кампании Ozon по кабинету или владельцу.
     */
    public OzonPromotionCampaign resolveCampaignForDetail(Long campaignId, Long cabinetId, Long sellerId) {
        if (cabinetId != null) {
            Optional<OzonPromotionCampaign> byCabinet = ozonPromotionCampaignRepository
                    .findByCampaignIdAndCabinet_Id(campaignId, cabinetId);
            if (byCabinet.isPresent()) {
                return byCabinet.get();
            }
        }
        if (sellerId != null) {
            Optional<OzonPromotionCampaign> campaignOpt = ozonPromotionCampaignRepository.findById(campaignId);
            if (campaignOpt.isPresent()) {
                OzonPromotionCampaign campaign = campaignOpt.get();
                if (campaign.getCabinet() != null
                        && campaign.getCabinet().getUser() != null
                        && campaign.getCabinet().getUser().getId().equals(sellerId)) {
                    return campaign;
                }
            }
        }
        return null;
    }

    private Map<Long, Integer> loadArticlesCount(List<Long> campaignIds) {
        Map<Long, Integer> articlesCountByCampaign = new HashMap<>();
        for (Object[] row : ozonCampaignArticleRepository.countByCampaignIdIn(campaignIds)) {
            articlesCountByCampaign.put((Long) row[0], ((Number) row[1]).intValue());
        }
        return articlesCountByCampaign;
    }

    private ArticleSummaryDto mapCampaignArticle(OzonCampaignArticle link, Long cabinetId) {
        Long sku = link.getSku();
        return articleCatalogQuery.findByCabinetAndSku(cabinetId, sku).stream()
                .findFirst()
                .map(card -> ArticleSummaryDto.builder()
                        .nmId(sku)
                        .productId(card.getProductId())
                        .offerId(card.getOfferId())
                        .marketplaceType(MarketplaceType.OZON)
                        .title(card.getTitle())
                        .photoTm(card.getPhotoUrl())
                        .build())
                .orElseGet(() -> ArticleSummaryDto.builder()
                        .nmId(sku)
                        .marketplaceType(MarketplaceType.OZON)
                        .title("SKU " + sku)
                        .build());
    }

    private CampaignDto buildCampaignDto(
            OzonPromotionCampaign campaign,
            List<OzonPromotionCampaignStatistics> stats,
            Integer articlesCount,
            Integer cart
    ) {
        boolean running = "CAMPAIGN_STATE_RUNNING".equals(campaign.getState());
        String type = OzonCampaignTypeLabels.format(campaign.getAdvObjectType(), campaign.getPaymentType());

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
                .statusName(formatCampaignState(campaign.getState()))
                .createdAt(campaign.getOzonCreatedAt())
                .updatedAt(campaign.getOzonUpdatedAt() != null ? campaign.getOzonUpdatedAt() : campaign.getSyncedAt())
                .views(views)
                .clicks(clicks)
                .ctr(ctr)
                .cpc(cpc)
                .costs(spend)
                .cart(cart != null && cart > 0 ? cart : null)
                .orders(orders)
                .articlesCount(articlesCount)
                .bidderStatus(resolveBidderStatus(campaign.getState()))
                .build();
    }

    private CampaignDto buildCampaignDtoFromProductStats(
            OzonPromotionCampaign campaign,
            List<OzonPromotionCampaignProductStatistics> stats,
            Integer articlesCount
    ) {
        boolean running = "CAMPAIGN_STATE_RUNNING".equals(campaign.getState());
        String type = OzonCampaignTypeLabels.format(campaign.getAdvObjectType(), campaign.getPaymentType());

        int views = 0;
        int clicks = 0;
        BigDecimal spend = BigDecimal.ZERO;
        int cart = 0;
        int orders = 0;
        for (OzonPromotionCampaignProductStatistics s : stats) {
            if (s.getViews() != null) {
                views += s.getViews();
            }
            if (s.getClicks() != null) {
                clicks += s.getClicks();
            }
            if (s.getSpend() != null) {
                spend = spend.add(s.getSpend());
            }
            if (s.getToCart() != null) {
                cart += s.getToCart();
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
                .statusName(formatCampaignState(campaign.getState()))
                .createdAt(campaign.getOzonCreatedAt())
                .updatedAt(campaign.getOzonUpdatedAt() != null ? campaign.getOzonUpdatedAt() : campaign.getSyncedAt())
                .views(views)
                .clicks(clicks)
                .ctr(ctr)
                .cpc(cpc)
                .costs(spend)
                .cart(cart > 0 ? cart : null)
                .orders(orders)
                .articlesCount(articlesCount)
                .bidderStatus(resolveBidderStatus(campaign.getState()))
                .build();
    }

    private static String resolveBidderStatus(String state) {
        if ("CAMPAIGN_STATE_RUNNING".equals(state)) {
            return BidderStatus.RUNNING.name();
        }
        if ("CAMPAIGN_STATE_STOPPED".equals(state) || "CAMPAIGN_STATE_INACTIVE".equals(state)) {
            return BidderStatus.OFF.name();
        }
        return BidderStatus.WAITING.name();
    }

    private static String formatCampaignState(String state) {
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

    private static String mapCampaignStateLabel(String state) {
        if (state == null || state.isBlank()) {
            return "—";
        }
        return switch (state) {
            case "CAMPAIGN_STATE_RUNNING" -> "Активна";
            case "CAMPAIGN_STATE_INACTIVE" -> "Неактивна";
            case "CAMPAIGN_STATE_PLANNED" -> "Запланирована";
            case "CAMPAIGN_STATE_STOPPED" -> "Остановлена";
            case "CAMPAIGN_STATE_FINISHED" -> "Завершена";
            default -> state;
        };
    }
}
