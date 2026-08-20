package ru.oparin.solution.service.campaign;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.oparin.solution.model.WbCampaignGoal;
import ru.oparin.solution.repository.WbCampaignGoalRepository;
import ru.oparin.solution.repository.WbPromotionCampaignRepository;

import java.util.Optional;

/**
 * Хранение и обновление цели на рекламную кампанию.
 */
@Service
@RequiredArgsConstructor
public class WbCampaignGoalService {

    private final WbCampaignGoalRepository goalRepository;
    private final WbPromotionCampaignRepository campaignRepository;

    @Transactional(readOnly = true)
    public Optional<String> findGoalText(Long cabinetId, Long campaignId) {
        if (cabinetId == null || campaignId == null) {
            return Optional.empty();
        }
        return goalRepository.findByCabinetIdAndCampaignId(cabinetId, campaignId).map(WbCampaignGoal::getGoalText);
    }

    /**
     * Создаёт или обновляет цель на рекламную кампанию в кабинете.
     */
    @Transactional
    public void upsertGoal(Long cabinetId, Long campaignId, String goal) {
        if (cabinetId == null) {
            throw new IllegalArgumentException("Кабинет не указан");
        }
        if (!campaignRepository.findByAdvertIdAndCabinet_Id(campaignId, cabinetId).isPresent()) {
            throw new IllegalArgumentException("Кампания не найдена в этом кабинете");
        }
        String text = goal != null ? goal : "";
        WbCampaignGoal entity = goalRepository.findByCabinetIdAndCampaignId(cabinetId, campaignId)
                .orElseGet(() -> WbCampaignGoal.builder()
                        .cabinetId(cabinetId)
                        .campaignId(campaignId)
                        .goalText("")
                        .build());
        entity.setGoalText(text);
        goalRepository.save(entity);
    }
}
