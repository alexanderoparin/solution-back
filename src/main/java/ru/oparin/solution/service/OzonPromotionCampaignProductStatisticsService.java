package ru.oparin.solution.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.oparin.solution.dto.ozon.OzonPerformanceProductStatsResponse;
import ru.oparin.solution.model.OzonPromotionCampaign;
import ru.oparin.solution.model.OzonPromotionCampaignProductStatistics;
import ru.oparin.solution.repository.OzonPromotionCampaignProductStatisticsRepository;
import ru.oparin.solution.repository.OzonPromotionCampaignRepository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Сохранение дневной статистики SKU в РК Ozon Performance.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OzonPromotionCampaignProductStatisticsService {

    private final OzonPromotionCampaignProductStatisticsRepository statisticsRepository;
    private final OzonPromotionCampaignRepository campaignRepository;

    /**
     * Сохраняет или обновляет строки product-stats из async-отчёта.
     *
     * @return число сохранённых строк
     */
    @Transactional
    public int saveOrUpdate(List<OzonPerformanceProductStatsResponse.Row> rows) {
        if (rows == null || rows.isEmpty()) {
            return 0;
        }
        Map<Long, OzonPromotionCampaign> campaignsById = new HashMap<>();
        int saved = 0;
        for (OzonPerformanceProductStatsResponse.Row row : rows) {
            if (row.getCampaignId() == null || row.getSku() == null || row.getDate() == null) {
                continue;
            }
            OzonPromotionCampaign campaign = campaignsById.computeIfAbsent(
                    row.getCampaignId(),
                    id -> campaignRepository.findById(id).orElse(null)
            );
            if (campaign == null) {
                log.debug("Ozon product stats: кампания {} отсутствует в БД, строка пропущена", row.getCampaignId());
                continue;
            }
            Optional<OzonPromotionCampaignProductStatistics> existing =
                    statisticsRepository.findByCampaign_CampaignIdAndSkuAndDate(
                            row.getCampaignId(), row.getSku(), row.getDate());
            OzonPromotionCampaignProductStatistics stat = existing.orElseGet(() ->
                    OzonPromotionCampaignProductStatistics.builder()
                            .campaign(campaign)
                            .sku(row.getSku())
                            .date(row.getDate())
                            .build());
            stat.setViews(row.getViews());
            stat.setClicks(row.getClicks());
            stat.setCtr(row.getCtr());
            stat.setToCart(row.getToCart());
            stat.setAvgCpc(row.getAvgCpc());
            stat.setSpend(row.getSpend());
            stat.setOrders(row.getOrders());
            stat.setOrdersMoney(row.getOrdersMoney());
            stat.setModelOrders(row.getModelOrders());
            stat.setModelSales(row.getModelSales());
            stat.setDrr(row.getDrr());
            statisticsRepository.save(stat);
            saved++;
        }
        log.info("Ozon product stats: сохранено/обновлено {} строк", saved);
        return saved;
    }
}
