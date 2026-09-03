package ru.oparin.solution.service.analytics.wb;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.oparin.solution.dto.analytics.*;
import ru.oparin.solution.model.*;
import ru.oparin.solution.repository.*;
import ru.oparin.solution.service.WbPromotionNormQueryStatisticsService;
import ru.oparin.solution.service.analytics.AnalyticsDateRangeResolver;
import ru.oparin.solution.service.analytics.AnalyticsDateRangeResolver.AnalyticsDateRange;
import ru.oparin.solution.service.analytics.MathUtils;
import ru.oparin.solution.service.campaign.BidderStatusResolver;
import ru.oparin.solution.service.campaign.WbCampaignGoalService;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Чтение рекламных кампаний WB: список, деталь, кластеры, РК на карточке артикула.
 */
@Service
@RequiredArgsConstructor
public class WbCampaignAnalyticsQuery {

    private final WbPromotionCampaignRepository campaignRepository;
    private final WbCampaignArticleRepository campaignArticleRepository;
    private final WbPromotionCampaignStatisticsRepository campaignStatisticsRepository;
    private final WbCampaignManagementStateRepository campaignManagementStateRepository;
    private final WbCampaignScheduleSlotRepository campaignScheduleSlotRepository;
    private final WbProductCardRepository productCardRepository;
    private final BidderStatusResolver bidderStatusResolver;
    private final WbCampaignGoalService campaignGoalService;
    private final WbPromotionNormQueryStatisticsService normQueryStatisticsService;
    private final WbArticleCatalogQuery articleCatalogQuery;

    /**
     * Список РК кабинета с метриками за период (включая завершённые).
     */
    @Transactional(readOnly = true)
    public List<CampaignDto> listByCabinet(
            Long cabinetId,
            LocalDate dateFrom,
            LocalDate dateTo,
            User seller,
            Long nmIdFilter
    ) {
        if (cabinetId == null) {
            return Collections.emptyList();
        }
        AnalyticsDateRange range = AnalyticsDateRangeResolver.resolve(dateFrom, dateTo);
        LocalDate from = range.startDate();
        LocalDate to = range.endDate();

        List<WbPromotionCampaign> campaigns = campaignRepository.findByCabinet_Id(cabinetId);
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
     * РК артикула для страницы карточки (включая завершённые).
     */
    @Transactional(readOnly = true)
    public List<CampaignDto> getCampaignsForArticle(
            Long nmId,
            Long cabinetId,
            LocalDate campaignDateFrom,
            LocalDate campaignDateTo
    ) {
        List<WbCampaignArticle> campaignArticles = campaignArticleRepository.findByNmId(nmId);

        List<WbPromotionCampaign> campaigns = campaignArticles.stream()
                .map(WbCampaignArticle::getCampaign)
                .filter(Objects::nonNull)
                .filter(campaign -> cabinetId == null
                        || (campaign.getCabinet() != null && campaign.getCabinet().getId().equals(cabinetId)))
                .distinct()
                .collect(Collectors.toList());

        if (campaigns.isEmpty()) {
            return Collections.emptyList();
        }

        boolean withMetrics = campaignDateFrom != null && campaignDateTo != null;
        AnalyticsDateRange metricsRange = withMetrics
                ? AnalyticsDateRangeResolver.resolve(campaignDateFrom, campaignDateTo)
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
     * Детали РК: название, статус, артикулы комбо.
     */
    @Transactional(readOnly = true)
    public CampaignDetailDto getCampaignDetail(Long campaignId, Long cabinetId, Long sellerId) {
        WbPromotionCampaign campaign = resolveCampaignForDetail(campaignId, cabinetId, sellerId);
        Long cabinetIdForArticles = cabinetId;
        if (campaign != null && campaign.getCabinet() != null) {
            cabinetIdForArticles = campaign.getCabinet().getId();
        }
        if (campaign == null || cabinetIdForArticles == null) {
            return null;
        }
        final Long finalCabinetId = cabinetIdForArticles;
        List<WbCampaignArticle> campaignArticles = campaignArticleRepository.findByCampaignId(campaign.getAdvertId());
        List<Long> nmIds = campaignArticles.stream()
                .map(WbCampaignArticle::getNmId)
                .distinct()
                .collect(Collectors.toList());
        boolean itemRatingSupported = articleCatalogQuery.isItemRatingSupported(null, finalCabinetId);
        List<ArticleSummaryDto> articles = nmIds.stream()
                .map(nmId -> productCardRepository.findByNmIdAndCabinet_Id(nmId, finalCabinetId)
                        .map(card -> articleCatalogQuery.mapToArticleSummary(card, itemRatingSupported))
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
     * Кластеры normquery кампании WB.
     */
    @Transactional(readOnly = true)
    public NormQueryClustersResponseDto getNormQueryClusters(
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
        return normQueryStatisticsService.getAggregatedClustersPage(
                campaign.getAdvertId(),
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
     * advertId кампаний кабинета или всех кабинетов продавца.
     */
    public List<Long> getCampaignIdsForCabinet(Long sellerId, Long cabinetId) {
        List<WbPromotionCampaign> campaigns = cabinetId != null
                ? campaignRepository.findByCabinet_Id(cabinetId)
                : campaignRepository.findByCabinet_User_Id(sellerId);
        return campaigns.stream().map(WbPromotionCampaign::getAdvertId).collect(Collectors.toList());
    }

    /**
     * Поиск РК по кабинету или владельцу.
     */
    public WbPromotionCampaign resolveCampaignForDetail(Long campaignId, Long cabinetId, Long sellerId) {
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
}
