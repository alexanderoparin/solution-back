package ru.oparin.solution.service.sync;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.oparin.solution.dto.ozon.OzonPerformanceCampaignListResponse;
import ru.oparin.solution.dto.ozon.OzonPerformanceDailyStatsResponse;
import ru.oparin.solution.dto.ozon.OzonPerformanceProductStatsResponse;
import ru.oparin.solution.dto.ozon.OzonPerformanceReportStatusResponse;
import ru.oparin.solution.model.Cabinet;
import ru.oparin.solution.model.OzonPromotionCampaign;
import ru.oparin.solution.repository.OzonPromotionCampaignRepository;
import ru.oparin.solution.service.OzonCampaignArticleService;
import ru.oparin.solution.service.OzonPromotionCampaignProductStatisticsService;
import ru.oparin.solution.service.OzonPromotionCampaignService;
import ru.oparin.solution.service.OzonPromotionCampaignStatisticsService;
import ru.oparin.solution.service.ozon.OzonPerformanceApiClient;

import java.time.LocalDate;
import java.util.List;

/**
 * Синхронизация списка РК, объектов (SKU) и дневной статистики Ozon Performance API.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OzonPromotionCampaignSyncService {

    private final OzonPerformanceApiClient performanceApiClient;
    private final OzonPromotionCampaignService campaignService;
    private final OzonPromotionCampaignStatisticsService statisticsService;
    private final OzonPromotionCampaignProductStatisticsService productStatisticsService;
    private final OzonCampaignArticleService campaignArticleService;
    private final OzonPromotionCampaignRepository campaignRepository;

    /**
     * Загружает все страницы списка кампаний и сохраняет в БД.
     *
     * @return количество сохранённых кампаний
     */
    public int syncCampaigns(Cabinet cabinet, String clientId, String clientSecret) {
        List<OzonPerformanceCampaignListResponse.Item> items = performanceApiClient.listAllCampaigns(
                cabinet.getId(), clientId, clientSecret);
        log.info("Ozon campaigns sync: получено {} кампаний для cabinetId={}", items.size(), cabinet.getId());
        return campaignService.saveOrUpdateCampaigns(cabinet, items);
    }

    /**
     * Обновляет список кампаний, SKU в РК и дневную статистику за период.
     *
     * @return число сохранённых строк статистики
     */
    public int syncCampaignsAndDailyStats(
            Cabinet cabinet,
            String clientId,
            String clientSecret,
            LocalDate dateFrom,
            LocalDate dateTo
    ) {
        syncCampaigns(cabinet, clientId, clientSecret);
        syncCampaignObjects(cabinet, clientId, clientSecret);
        int dailyRows = syncDailyStats(cabinet, clientId, clientSecret, dateFrom, dateTo);
        log.info("Ozon sync campaigns+stats: dailyRows={} cabinetId={}", dailyRows, cabinet.getId());
        return dailyRows;
    }

    /**
     * Async product-level stats для одного батча кампаний (POST → poll → download).
     * Может вернуть {@link OzonProductStatsSyncResult.Status#PENDING} — тогда нужен повтор с тем же UUID.
     */
    public OzonProductStatsSyncResult syncProductStatsBatch(
            Cabinet cabinet,
            String clientId,
            String clientSecret,
            LocalDate dateFrom,
            LocalDate dateTo,
            String existingReportUuid,
            int campaignBatchStart
    ) {
        List<Long> allCampaignIds = campaignRepository.findByCabinet_Id(cabinet.getId()).stream()
                .map(OzonPromotionCampaign::getCampaignId)
                .toList();
        if (allCampaignIds.isEmpty()) {
            return OzonProductStatsSyncResult.completed(0);
        }
        int batchSize = performanceApiClient.getProductStatsCampaignBatchSize();
        int batchStart = Math.max(0, campaignBatchStart);
        if (batchStart >= allCampaignIds.size()) {
            return OzonProductStatsSyncResult.completed(0);
        }
        List<Long> batchIds = allCampaignIds.subList(
                batchStart,
                Math.min(batchStart + batchSize, allCampaignIds.size())
        );
        Long fallbackCampaignId = batchIds.size() == 1 ? batchIds.get(0) : null;

        String reportUuid = existingReportUuid;
        if (reportUuid == null || reportUuid.isBlank()) {
            reportUuid = performanceApiClient.submitProductStatisticsReport(
                    cabinet.getId(), clientId, clientSecret, batchIds, dateFrom, dateTo);
        }

        try {
            OzonPerformanceReportStatusResponse status = performanceApiClient.waitForReportReady(
                    cabinet.getId(), clientId, clientSecret, reportUuid);
            String state = status != null && status.getState() != null
                    ? status.getState().trim().toUpperCase()
                    : "";
            if ("IN_PROGRESS".equals(state) || "NOT_STARTED".equals(state)) {
                return OzonProductStatsSyncResult.pending(reportUuid);
            }
            if ("ERROR".equals(state)) {
                String err = status.getError() != null ? status.getError() : "ERROR";
                log.warn("Ozon product stats report ERROR uuid={}, batchStart={}: {}",
                        reportUuid, batchStart, err);
                return OzonProductStatsSyncResult.skipped(err);
            }
            if (!"OK".equals(state)) {
                return OzonProductStatsSyncResult.pending(reportUuid);
            }

            List<OzonPerformanceProductStatsResponse.Row> rows = performanceApiClient.downloadProductStatisticsReport(
                    cabinet.getId(), clientId, clientSecret, reportUuid, fallbackCampaignId);
            int saved = productStatisticsService.saveOrUpdate(rows);
            log.info("Ozon product stats sync: cabinetId={}, batchStart={}, saved={}",
                    cabinet.getId(), batchStart, saved);
            return OzonProductStatsSyncResult.completed(saved);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return OzonProductStatsSyncResult.pending(reportUuid);
        }
    }

    /**
     * Есть ли ещё батчи product-stats после {@code campaignBatchStart}.
     */
    public boolean hasMoreProductStatsBatches(Long cabinetId, int campaignBatchStart) {
        int total = campaignRepository.findByCabinet_Id(cabinetId).size();
        int batchSize = performanceApiClient.getProductStatsCampaignBatchSize();
        return campaignBatchStart + batchSize < total;
    }

    public int getProductStatsCampaignBatchSize() {
        return performanceApiClient.getProductStatsCampaignBatchSize();
    }

    /**
     * Для каждой кампании кабинета загружает список SKU.
     */
    public int syncCampaignObjects(Cabinet cabinet, String clientId, String clientSecret) {
        List<OzonPromotionCampaign> campaigns = campaignRepository.findByCabinet_Id(cabinet.getId());
        int totalLinks = 0;
        for (OzonPromotionCampaign campaign : campaigns) {
            if ("CAMPAIGN_STATE_FINISHED".equals(campaign.getState())) {
                continue;
            }
            try {
                List<Long> skus = performanceApiClient.listCampaignSkus(
                        cabinet.getId(), clientId, clientSecret, campaign.getCampaignId());
                totalLinks += campaignArticleService.replaceCampaignSkus(
                        cabinet.getId(), campaign.getCampaignId(), skus);
            } catch (Exception e) {
                log.warn("Ozon campaign objects: ошибка для campaignId={}: {}",
                        campaign.getCampaignId(), e.getMessage());
            }
        }
        log.info("Ozon campaign objects sync: cabinetId={}, всего связей SKU={}", cabinet.getId(), totalLinks);
        return totalLinks;
    }

    /**
     * Загружает дневную статистику по кампаниям кабинета за период.
     */
    public int syncDailyStats(
            Cabinet cabinet,
            String clientId,
            String clientSecret,
            LocalDate dateFrom,
            LocalDate dateTo
    ) {
        List<Long> campaignIds = campaignRepository.findByCabinet_Id(cabinet.getId()).stream()
                .map(OzonPromotionCampaign::getCampaignId)
                .toList();
        List<OzonPerformanceDailyStatsResponse.Row> rows = performanceApiClient.getDailyStatistics(
                cabinet.getId(), clientId, clientSecret, campaignIds, dateFrom, dateTo);
        log.info("Ozon daily stats sync: получено {} строк для cabinetId={}, период={}..{}",
                rows.size(), cabinet.getId(), dateFrom, dateTo);
        return statisticsService.saveOrUpdate(rows);
    }
}
