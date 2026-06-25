package ru.oparin.solution.service.campaign;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.oparin.solution.model.Cabinet;
import ru.oparin.solution.model.CampaignManagementState;
import ru.oparin.solution.model.CampaignStatus;
import ru.oparin.solution.model.PromotionCampaign;
import ru.oparin.solution.repository.PromotionCampaignRepository;
import ru.oparin.solution.service.CabinetService;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Сбор РК кабинета, которым в тике планировщика нужен опрос бюджета WB.
 */
@Service
@RequiredArgsConstructor
public class CampaignSchedulePollPlanner {

    private final PromotionCampaignRepository campaignRepository;
    private final CabinetService cabinetService;
    private final CampaignBudgetPollEligibility pollEligibility;

    /**
     * Кандидаты на опрос бюджета: активный слот без исчерпанного лимита или хвост trail после паузы.
     */
    public Map<Long, List<Long>> collectBudgetPollCandidates(
            List<CampaignManagementState> states,
            ZonedDateTime now
    ) {
        Map<Long, List<Long>> byCabinet = new LinkedHashMap<>();
        for (CampaignManagementState state : states) {
            if (!state.isScheduleEnabled()) {
                continue;
            }
            Long advertId = state.getCampaignId();
            Long cabinetId = state.getCabinetId();
            Cabinet cabinet = cabinetService.findById(cabinetId).orElse(null);
            if (cabinet == null || cabinet.getApiKey() == null || cabinet.getApiKey().isBlank()) {
                continue;
            }
            PromotionCampaign campaign = campaignRepository.findByAdvertIdAndCabinet_Id(advertId, cabinetId).orElse(null);
            if (campaign == null || campaign.getStatus() == CampaignStatus.FINISHED) {
                continue;
            }
            if (!pollEligibility.needsBudgetPoll(state, advertId, cabinetId, now)) {
                continue;
            }
            byCabinet.computeIfAbsent(cabinetId, ignored -> new ArrayList<>()).add(advertId);
        }
        return byCabinet;
    }
}
