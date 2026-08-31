package ru.oparin.solution.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.oparin.solution.dto.analytics.*;
import ru.oparin.solution.model.User;
import ru.oparin.solution.model.WbProductCard;
import ru.oparin.solution.service.analytics.*;

import java.time.LocalDate;
import java.util.List;

/**
 * Фасад аналитики: маршрутизация к query-сервисам каталога, сводной, карточки, рекламы и остатков.
 */
@Service
@RequiredArgsConstructor
public class AnalyticsService {

    private final AnalyticsSummaryQuery analyticsSummaryQuery;
    private final ArticleCatalogQuery articleCatalogQuery;
    private final ArticleAnalyticsQuery articleAnalyticsQuery;
    private final CampaignAnalyticsQuery campaignAnalyticsQuery;
    private final AnalyticsStockQuery analyticsStockQuery;

    /**
     * Сводная аналитика для продавца (при cabinetId != null — только по выбранному кабинету).
     */
    @Transactional(readOnly = true)
    public SummaryResponseDto getSummary(User seller, Long cabinetId, AnalyticsSummaryRequest request) {
        return analyticsSummaryQuery.getSummary(seller, cabinetId, request);
    }

    /**
     * Список артикулов кабинета/продавца — справочник для фильтра.
     */
    @Transactional(readOnly = true)
    public List<ArticleSummaryDto> getArticleList(
            User seller,
            Long cabinetId,
            Boolean onlyWithPhoto,
            Boolean onlyPriority,
            Boolean onlyInAdvertising
    ) {
        return articleCatalogQuery.getArticleList(
                seller, cabinetId, onlyWithPhoto, onlyPriority, onlyInAdvertising);
    }

    /**
     * Детальные метрики по группе для всех артикулов.
     */
    @Transactional(readOnly = true)
    public MetricGroupResponseDto getMetricGroup(
            User seller,
            Long cabinetId,
            String metricName,
            AnalyticsSummaryRequest request
    ) {
        return analyticsSummaryQuery.getMetricGroup(seller, cabinetId, metricName, request);
    }

    /**
     * Детальная информация по артикулу.
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
        return articleAnalyticsQuery.getArticle(
                seller,
                cabinetId,
                nmId,
                periods,
                campaignDateFrom,
                campaignDateTo,
                dailyDataDateFrom,
                dailyDataDateTo,
                dailyDataCampaignAdvertId
        );
    }

    /**
     * Список рекламных кампаний кабинета WB с агрегированной статистикой за период.
     */
    @Transactional(readOnly = true)
    public List<CampaignDto> listCampaignsByCabinet(
            Long cabinetId,
            LocalDate dateFrom,
            LocalDate dateTo,
            User seller,
            Long nmIdFilter
    ) {
        return campaignAnalyticsQuery.listCampaignsByCabinet(cabinetId, dateFrom, dateTo, seller, nmIdFilter);
    }

    /**
     * Список рекламных кампаний Ozon с агрегацией дневной статистики за период.
     */
    @Transactional(readOnly = true)
    public List<CampaignDto> listOzonCampaignsByCabinet(Long cabinetId, LocalDate dateFrom, LocalDate dateTo) {
        return campaignAnalyticsQuery.listOzonCampaignsByCabinet(cabinetId, dateFrom, dateTo);
    }

    /**
     * Детали рекламной кампании (WB или Ozon).
     */
    @Transactional(readOnly = true)
    public CampaignDetailDto getCampaignDetail(Long campaignId, Long cabinetId, Long sellerId) {
        return campaignAnalyticsQuery.getCampaignDetail(campaignId, cabinetId, sellerId);
    }

    /**
     * Агрегированная статистика по поисковым кластерам кампании за период.
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
        return campaignAnalyticsQuery.getCampaignNormQueryClusters(
                campaignId, cabinetId, sellerId, dateFrom, dateTo, nmId, search, sortBy, sortDir, page, size);
    }

    /**
     * Детализация остатков FBO по размерам на складе WB.
     */
    @Transactional(readOnly = true)
    public List<StockSizeDto> getStockSizes(Long nmId, String warehouseName, Long warehouseId, Long cabinetId) {
        return analyticsStockQuery.getStockSizes(nmId, warehouseName, warehouseId, cabinetId);
    }

    /**
     * Детализация остатков FBS по размерам на складе продавца.
     */
    @Transactional(readOnly = true)
    public List<StockSizeDto> getFbsStockSizes(Long nmId, String warehouseName, Long warehouseId, Long cabinetId) {
        return analyticsStockQuery.getFbsStockSizes(nmId, warehouseName, warehouseId, cabinetId);
    }

    /**
     * Карточка артикула WB с проверкой владельца.
     */
    @Transactional(readOnly = true)
    public WbProductCard findCardBySeller(Long nmId, Long sellerId) {
        return findCardBySeller(nmId, sellerId, null);
    }

    /**
     * Карточка артикула WB с проверкой владельца и кабинета.
     */
    @Transactional(readOnly = true)
    public WbProductCard findCardBySeller(Long nmId, Long sellerId, Long cabinetId) {
        return articleCatalogQuery.findCardBySeller(nmId, sellerId, cabinetId);
    }

    /**
     * Сохраняет флаг приоритета артикула.
     */
    @Transactional
    public void updateArticlePriority(User seller, Long cabinetId, Long nmId, boolean isPriority) {
        articleCatalogQuery.updateArticlePriority(seller, cabinetId, nmId, isPriority);
    }
}
