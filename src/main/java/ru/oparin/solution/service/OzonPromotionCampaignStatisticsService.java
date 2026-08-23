package ru.oparin.solution.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.oparin.solution.dto.ozon.OzonPerformanceDailyStatsResponse;
import ru.oparin.solution.model.OzonPromotionCampaign;
import ru.oparin.solution.model.OzonPromotionCampaignStatistics;
import ru.oparin.solution.repository.OzonPromotionCampaignRepository;
import ru.oparin.solution.repository.OzonPromotionCampaignStatisticsRepository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Сохранение дневной статистики РК Ozon.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OzonPromotionCampaignStatisticsService {

    private final OzonPromotionCampaignStatisticsRepository statisticsRepository;
    private final OzonPromotionCampaignRepository campaignRepository;

    /**
     * Upsert строк статистики. Строки без известной кампании в БД пропускаются.
     *
     * @return число сохранённых/обновлённых записей
     */
    @Transactional
    public int saveOrUpdate(List<OzonPerformanceDailyStatsResponse.Row> rows) {
        if (rows == null || rows.isEmpty()) {
            return 0;
        }
        Map<Long, OzonPromotionCampaign> campaignsById = new HashMap<>();
        int saved = 0;
        for (OzonPerformanceDailyStatsResponse.Row row : rows) {
            if (row.getCampaignId() == null || row.getDate() == null) {
                continue;
            }
            OzonPromotionCampaign campaign = campaignsById.computeIfAbsent(
                    row.getCampaignId(),
                    id -> campaignRepository.findById(id).orElse(null)
            );
            if (campaign == null) {
                log.debug("Ozon daily stats: кампания {} отсутствует в БД, строка пропущена", row.getCampaignId());
                continue;
            }
            Optional<OzonPromotionCampaignStatistics> existing =
                    statisticsRepository.findByCampaign_CampaignIdAndDate(row.getCampaignId(), row.getDate());
            OzonPromotionCampaignStatistics stat = existing.orElseGet(() ->
                    OzonPromotionCampaignStatistics.builder()
                            .campaign(campaign)
                            .date(row.getDate())
                            .build()
            );
            stat.setViews(row.getViews());
            stat.setClicks(row.getClicks());
            stat.setSpend(row.getSpend());
            stat.setAvgBid(row.getAvgBid());
            stat.setOrders(row.getOrders());
            stat.setOrdersMoney(row.getOrdersMoney());
            statisticsRepository.save(stat);
            saved++;
        }
        log.info("Ozon daily stats: сохранено/обновлено {} строк", saved);
        return saved;
    }
}
