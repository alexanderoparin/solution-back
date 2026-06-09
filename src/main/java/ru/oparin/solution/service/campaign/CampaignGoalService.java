package ru.oparin.solution.service.campaign;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.oparin.solution.model.CampaignGoal;
import ru.oparin.solution.repository.CampaignGoalRepository;
import ru.oparin.solution.repository.PromotionCampaignRepository;

import java.util.Optional;

/**
 * Хранение и обновление цели на рекламную кампанию.
 */
@Service
@RequiredArgsConstructor
public class CampaignGoalService {

    private final CampaignGoalRepository goalRepository;
    private final PromotionCampaignRepository campaignRepository;

    @Transactional(readOnly = true)
    public Optional<String> findGoalText(Long cabinetId, Long campaignId) {
        if (cabinetId == null || campaignId == null) {
            return Optional.empty();
        }
        return goalRepository.findByCabinetIdAndCampaignId(cabinetId, campaignId).map(CampaignGoal::getGoalText);
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
        CampaignGoal entity = goalRepository.findByCabinetIdAndCampaignId(cabinetId, campaignId)
                .orElseGet(() -> CampaignGoal.builder()
                        .cabinetId(cabinetId)
                        .campaignId(campaignId)
                        .goalText("")
                        .build());
        entity.setGoalText(text);
        goalRepository.save(entity);
    }
}
