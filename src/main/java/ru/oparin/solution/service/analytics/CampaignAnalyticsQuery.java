package ru.oparin.solution.service.analytics;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.oparin.solution.dto.analytics.CampaignDetailDto;
import ru.oparin.solution.dto.analytics.CampaignDto;
import ru.oparin.solution.dto.analytics.NormQueryClustersResponseDto;
import ru.oparin.solution.model.User;
import ru.oparin.solution.service.analytics.ozon.OzonCampaignAnalyticsQuery;
import ru.oparin.solution.service.analytics.wb.WbCampaignAnalyticsQuery;

import java.time.LocalDate;
import java.util.List;

/**
 * Маршрутизация чтения рекламных кампаний WB и Ozon.
 */
@Service
@RequiredArgsConstructor
public class CampaignAnalyticsQuery {

    private final WbCampaignAnalyticsQuery wbCampaignAnalyticsQuery;
    private final OzonCampaignAnalyticsQuery ozonCampaignAnalyticsQuery;

    /**
     * Список РК кабинета WB с метриками за период.
     */
    @Transactional(readOnly = true)
    public List<CampaignDto> listCampaignsByCabinet(
            Long cabinetId,
            LocalDate dateFrom,
            LocalDate dateTo,
            User seller,
            Long nmIdFilter
    ) {
        return wbCampaignAnalyticsQuery.listByCabinet(cabinetId, dateFrom, dateTo, seller, nmIdFilter);
    }

    /**
     * Список РК кабинета Ozon с метриками за период.
     */
    @Transactional(readOnly = true)
    public List<CampaignDto> listOzonCampaignsByCabinet(Long cabinetId, LocalDate dateFrom, LocalDate dateTo) {
        return ozonCampaignAnalyticsQuery.listByCabinet(cabinetId, dateFrom, dateTo);
    }

    /**
     * Детали РК: сначала Ozon, затем WB.
     */
    @Transactional(readOnly = true)
    public CampaignDetailDto getCampaignDetail(Long campaignId, Long cabinetId, Long sellerId) {
        CampaignDetailDto ozonDetail = ozonCampaignAnalyticsQuery.getCampaignDetail(campaignId, cabinetId, sellerId);
        if (ozonDetail != null) {
            return ozonDetail;
        }
        return wbCampaignAnalyticsQuery.getCampaignDetail(campaignId, cabinetId, sellerId);
    }

    /**
     * Кластеры поисковых запросов: Ozon search phrases или WB normquery.
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
        LocalDate from = dateFrom != null
                ? dateFrom
                : LocalDate.now().minusDays(AnalyticsDateRangeResolver.DEFAULT_DAILY_DATA_SPAN_DAYS - 1);
        LocalDate to = dateTo != null ? dateTo : LocalDate.now();
        if (from.isAfter(to)) {
            LocalDate tmp = from;
            from = to;
            to = tmp;
        }

        if (ozonCampaignAnalyticsQuery.resolveCampaignForDetail(campaignId, cabinetId, sellerId) != null) {
            return ozonCampaignAnalyticsQuery.getSearchPhraseClusters(
                    campaignId, cabinetId, sellerId, from, to, nmId, search, sortBy, sortDir, page, size);
        }
        return wbCampaignAnalyticsQuery.getNormQueryClusters(
                campaignId, cabinetId, sellerId, from, to, nmId, search, sortBy, sortDir, page, size);
    }
}
