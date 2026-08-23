package ru.oparin.solution.service.sync;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.oparin.solution.dto.ozon.OzonPerformanceCampaignListResponse;
import ru.oparin.solution.dto.ozon.OzonPerformanceDailyStatsResponse;
import ru.oparin.solution.model.Cabinet;
import ru.oparin.solution.model.OzonPromotionCampaign;
import ru.oparin.solution.repository.OzonPromotionCampaignRepository;
import ru.oparin.solution.service.OzonCampaignArticleService;
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
        return syncDailyStats(cabinet, clientId, clientSecret, dateFrom, dateTo);
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
