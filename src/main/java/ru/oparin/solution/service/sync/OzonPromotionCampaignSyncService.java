package ru.oparin.solution.service.sync;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.oparin.solution.dto.ozon.OzonPerformanceCampaignListResponse;
import ru.oparin.solution.model.Cabinet;
import ru.oparin.solution.service.OzonPromotionCampaignService;
import ru.oparin.solution.service.ozon.OzonPerformanceApiClient;

import java.util.List;

/**
 * Синхронизация списка рекламных кампаний Ozon Performance API.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OzonPromotionCampaignSyncService {

    private final OzonPerformanceApiClient performanceApiClient;
    private final OzonPromotionCampaignService campaignService;

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
}
