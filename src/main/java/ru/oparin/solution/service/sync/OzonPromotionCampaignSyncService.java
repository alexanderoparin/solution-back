package ru.oparin.solution.service.sync;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import ru.oparin.solution.dto.ozon.*;
import ru.oparin.solution.model.Cabinet;
import ru.oparin.solution.model.OzonPromotionCampaign;
import ru.oparin.solution.repository.OzonPromotionCampaignRepository;
import ru.oparin.solution.service.*;
import ru.oparin.solution.service.ozon.OzonPerformanceApiClient;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Синхронизация списка РК, объектов (SKU) и дневной статистики Ozon Performance API.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OzonPromotionCampaignSyncService {

    /** Типы кампаний, для которых доступен список SKU через Performance API. */
    private static final Set<String> CAMPAIGN_OBJECTS_ADV_TYPES = Set.of("SKU");

    private final OzonPerformanceApiClient performanceApiClient;
    private final OzonPromotionCampaignService campaignService;
    private final OzonPromotionCampaignStatisticsService statisticsService;
    private final OzonPromotionCampaignProductStatisticsService productStatisticsService;
    private final OzonPromotionCampaignSearchPhraseStatisticsService searchPhraseStatisticsService;
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
                .filter(this::isProductStatsEligible)
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
            log.info("Ozon product stats sync: cabinetId={}, batchStart={}, parsed={}, campaignIds={}",
                    cabinet.getId(), batchStart, rows.size(), batchIds);
            int saved = productStatisticsService.saveOrUpdate(rows);
            if (saved == 0 && !rows.isEmpty()) {
                log.warn("Ozon product stats sync: parsed {} строк, но сохранено 0 — проверьте campaignId в БД",
                        rows.size());
            }
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
        int total = (int) campaignRepository.findByCabinet_Id(cabinetId).stream()
                .filter(this::isProductStatsEligible)
                .count();
        int batchSize = performanceApiClient.getProductStatsCampaignBatchSize();
        return campaignBatchStart + batchSize < total;
    }

    public int getProductStatsCampaignBatchSize() {
        return performanceApiClient.getProductStatsCampaignBatchSize();
    }

    /**
     * Async search-phrases stats для одного батча кампаний (POST → poll → download).
     */
    public OzonProductStatsSyncResult syncSearchPhrasesBatch(
            Cabinet cabinet,
            String clientId,
            String clientSecret,
            LocalDate dateFrom,
            LocalDate dateTo,
            String existingReportUuid,
            int campaignBatchStart
    ) {
        List<OzonPromotionCampaign> eligible = listSearchPhrasesEligibleCampaigns(cabinet.getId());
        if (eligible.isEmpty()) {
            return OzonProductStatsSyncResult.completed(0);
        }
        int batchSize = performanceApiClient.getSearchPhrasesCampaignBatchSize();
        int batchStart = Math.max(0, campaignBatchStart);
        if (batchStart >= eligible.size()) {
            return OzonProductStatsSyncResult.completed(0);
        }
        OzonPromotionCampaign campaign = eligible.get(batchStart);
        Long campaignId = campaign.getCampaignId();

        String reportUuid = existingReportUuid;
        if (reportUuid == null || reportUuid.isBlank()) {
            try {
                reportUuid = performanceApiClient.submitSearchPhrasesReport(
                        cabinet.getId(), clientId, clientSecret, List.of(campaignId), dateFrom, dateTo);
            } catch (HttpClientErrorException e) {
                if (e.getStatusCode().value() == 400) {
                    log.warn("Ozon search phrases submit skipped: campaignId={}, HTTP 400, body={}",
                            campaignId, e.getResponseBodyAsString());
                    return OzonProductStatsSyncResult.skipped("HTTP 400 для campaignId=" + campaignId);
                }
                throw e;
            }
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
                log.warn("Ozon search phrases report ERROR uuid={}, campaignId={}: {}",
                        reportUuid, campaignId, err);
                return OzonProductStatsSyncResult.skipped(err);
            }
            if (!"OK".equals(state)) {
                return OzonProductStatsSyncResult.pending(reportUuid);
            }

            List<OzonPerformanceSearchPhrasesResponse.Row> rows = performanceApiClient.downloadSearchPhrasesReport(
                    cabinet.getId(), clientId, clientSecret, reportUuid, campaignId, dateFrom, dateTo);
            log.info("Ozon search phrases sync: cabinetId={}, campaignId={}, parsed={}",
                    cabinet.getId(), campaignId, rows.size());
            int saved = searchPhraseStatisticsService.replaceForCampaign(campaign, dateFrom, dateTo, rows);
            log.info("Ozon search phrases sync: cabinetId={}, campaignId={}, saved={}",
                    cabinet.getId(), campaignId, saved);
            return OzonProductStatsSyncResult.completed(saved);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return OzonProductStatsSyncResult.pending(reportUuid);
        }
    }

    /**
     * Есть ли ещё батчи search-phrases после {@code campaignBatchStart}.
     */
    public boolean hasMoreSearchPhrasesBatches(Long cabinetId, int campaignBatchStart) {
        int total = listSearchPhrasesEligibleCampaigns(cabinetId).size();
        int batchSize = performanceApiClient.getSearchPhrasesCampaignBatchSize();
        return campaignBatchStart + batchSize < total;
    }

    public int getSearchPhrasesCampaignBatchSize() {
        return performanceApiClient.getSearchPhrasesCampaignBatchSize();
    }

    /**
     * Кампании, для которых доступен отчёт /statistics/phrases (активные SKU/CPC).
     */
    public List<OzonPromotionCampaign> listSearchPhrasesEligibleCampaigns(Long cabinetId) {
        return campaignRepository.findByCabinet_Id(cabinetId).stream()
                .filter(this::isSearchPhrasesEligible)
                .sorted(Comparator.comparing(OzonPromotionCampaign::getCampaignId))
                .toList();
    }

    private boolean isSearchPhrasesEligible(OzonPromotionCampaign campaign) {
        if (!isActiveCampaignState(campaign.getState())) {
            return false;
        }
        String advObjectType = campaign.getAdvObjectType();
        if (advObjectType == null || "SEARCH_PROMO".equals(advObjectType)) {
            return false;
        }
        if (campaign.getPaymentType() != null
                && campaign.getPaymentType().trim().toUpperCase(Locale.ROOT).contains("CPC")) {
            return true;
        }
        return "SKU".equals(advObjectType);
    }

    private static boolean isActiveCampaignState(String state) {
        if (state == null || state.isBlank()) {
            return false;
        }
        String normalized = state.trim().toUpperCase(Locale.ROOT);
        return "CAMPAIGN_STATE_RUNNING".equals(normalized);
    }

    private boolean isCampaignObjectsEligible(OzonPromotionCampaign campaign) {
        String advObjectType = campaign.getAdvObjectType();
        return advObjectType != null && CAMPAIGN_OBJECTS_ADV_TYPES.contains(advObjectType);
    }

    private boolean isProductStatsEligible(OzonPromotionCampaign campaign) {
        return isCampaignObjectsEligible(campaign);
    }

    /**
     * Для каждой кампании кабинета загружает список SKU.
     */
    public int syncCampaignObjects(Cabinet cabinet, String clientId, String clientSecret) {
        List<OzonPromotionCampaign> campaigns = campaignRepository.findByCabinet_Id(cabinet.getId());
        int totalLinks = 0;
        int skipped = 0;
        for (OzonPromotionCampaign campaign : campaigns) {
            if ("CAMPAIGN_STATE_FINISHED".equals(campaign.getState())) {
                continue;
            }
            if (!isCampaignObjectsEligible(campaign)) {
                skipped++;
                continue;
            }
            try {
                List<Long> skus = performanceApiClient.listCampaignSkus(
                        cabinet.getId(), clientId, clientSecret, campaign.getCampaignId());
                totalLinks += campaignArticleService.replaceCampaignSkus(
                        cabinet.getId(), campaign.getCampaignId(), skus);
            } catch (Exception e) {
                if (isCampaignNotFoundMessage(e.getMessage())) {
                    log.debug("Ozon campaign objects: кампания {} недоступна в Performance API",
                            campaign.getCampaignId());
                    continue;
                }
                log.warn("Ozon campaign objects: ошибка для campaignId={}: {}",
                        campaign.getCampaignId(), e.getMessage());
            }
        }
        log.info("Ozon campaign objects sync: cabinetId={}, всего связей SKU={}, пропущено типов={}",
                cabinet.getId(), totalLinks, skipped);
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

    private static boolean isCampaignNotFoundMessage(String message) {
        if (message == null || message.isBlank()) {
            return false;
        }
        String lower = message.toLowerCase(Locale.ROOT);
        return lower.contains("не найдена") || lower.contains("not found");
    }
}
