package ru.oparin.solution.service.campaign;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.oparin.solution.model.*;
import ru.oparin.solution.repository.PromotionCampaignRepository;
import ru.oparin.solution.service.CabinetService;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;

/**
 * Сбор РК кабинета, которым в тике планировщика нужен опрос бюджета WB.
 */
@Service
@RequiredArgsConstructor
public class CampaignSchedulePollPlanner {

    private static final ZoneId ZONE = ZoneId.of("Europe/Moscow");

    private final CampaignManageService manageService;
    private final PromotionCampaignRepository campaignRepository;
    private final CabinetService cabinetService;

    /**
     * Кандидаты на опрос бюджета: активный слот без исчерпанного лимита или хвост trail после паузы.
     */
    public Map<Long, List<Long>> collectBudgetPollCandidates(
            List<CampaignManagementState> states,
            ZonedDateTime now
    ) {
        Map<Long, List<Long>> byCabinet = new LinkedHashMap<>();
        LocalDateTime nowLocal = now.toLocalDateTime();
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
            if (!needsBudgetPoll(state, advertId, cabinetId, now, nowLocal)) {
                continue;
            }
            byCabinet.computeIfAbsent(cabinetId, ignored -> new ArrayList<>()).add(advertId);
        }
        return byCabinet;
    }

    private boolean needsBudgetPoll(
            CampaignManagementState state,
            Long advertId,
            Long cabinetId,
            ZonedDateTime now,
            LocalDateTime nowLocal
    ) {
        Optional<CampaignScheduleSlot> activeSlot = manageService.findActiveSlotNow(advertId, cabinetId, now);
        boolean inSlot = activeSlot.isPresent() && !state.isManualStopped();
        if (inSlot) {
            return !SlotBudgetSpendUtils.isSlotBudgetExhausted(state, activeSlot.get().getId());
        }
        LocalDateTime trailUntil = state.getBudgetTrailUntil();
        return trailUntil != null && !nowLocal.isAfter(trailUntil);
    }
}
