package ru.oparin.solution.service.sync;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.oparin.solution.dto.ozon.OzonPerformanceCampaignListResponse;
import ru.oparin.solution.dto.ozon.OzonPerformanceDailyStatsResponse;
import ru.oparin.solution.model.Cabinet;
import ru.oparin.solution.model.OzonPromotionCampaign;
import ru.oparin.solution.repository.OzonPromotionCampaignRepository;
import ru.oparin.solution.service.OzonPromotionCampaignService;
import ru.oparin.solution.service.OzonPromotionCampaignStatisticsService;
import ru.oparin.solution.service.ozon.OzonPerformanceApiClient;

import java.time.LocalDate;
import java.util.List;

/**
 * Синхронизация списка РК и дневной статистики Ozon Performance API.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OzonPromotionCampaignSyncService {

    private final OzonPerformanceApiClient performanceApiClient;
    private final OzonPromotionCampaignService campaignService;
    private final OzonPromotionCampaignStatisticsService statisticsService;
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
     * Обновляет список кампаний и дневную статистику за период.
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
        return syncDailyStats(cabinet, clientId, clientSecret, dateFrom, dateTo);
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
