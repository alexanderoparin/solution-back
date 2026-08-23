package ru.oparin.solution.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.oparin.solution.dto.ozon.OzonPerformanceCampaignListResponse;
import ru.oparin.solution.model.Cabinet;
import ru.oparin.solution.model.OzonPromotionCampaign;
import ru.oparin.solution.repository.OzonPromotionCampaignRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Сохранение рекламных кампаний Ozon в БД.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OzonPromotionCampaignService {

    private final OzonPromotionCampaignRepository campaignRepository;

    /**
     * Создаёт или обновляет кампании кабинета по данным Performance API.
     */
    @Transactional
    public int saveOrUpdateCampaigns(Cabinet cabinet, List<OzonPerformanceCampaignListResponse.Item> items) {
        if (items == null || items.isEmpty()) {
            return 0;
        }
        LocalDateTime now = LocalDateTime.now();
        List<Long> ids = items.stream()
                .map(OzonPerformanceCampaignListResponse.Item::getId)
                .filter(id -> id != null)
                .toList();
        Map<Long, OzonPromotionCampaign> existing = campaignRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(OzonPromotionCampaign::getCampaignId, Function.identity()));

        int saved = 0;
        for (OzonPerformanceCampaignListResponse.Item item : items) {
            if (item.getId() == null) {
                continue;
            }
            OzonPromotionCampaign campaign = existing.get(item.getId());
            if (campaign == null) {
                campaign = OzonPromotionCampaign.builder()
                        .campaignId(item.getId())
                        .cabinet(cabinet)
                        .build();
            }
            campaign.setTitle(item.getTitle() != null ? item.getTitle() : "Кампания " + item.getId());
            campaign.setState(item.getState() != null ? item.getState() : "CAMPAIGN_STATE_UNKNOWN");
            campaign.setAdvObjectType(item.getAdvObjectType());
            campaign.setPaymentType(item.getPaymentType());
            campaign.setDailyBudget(item.getDailyBudget());
            campaign.setBudget(item.getBudget());
            campaign.setFromDate(item.getFromDate());
            campaign.setToDate(item.getToDate());
            campaign.setOzonCreatedAt(item.getCreatedAt());
            campaign.setOzonUpdatedAt(item.getUpdatedAt());
            campaign.setSyncedAt(now);
            campaignRepository.save(campaign);
            saved++;
        }
        log.info("Ozon campaigns: сохранено/обновлено {} кампаний для cabinetId={}", saved, cabinet.getId());
        return saved;
    }
}
