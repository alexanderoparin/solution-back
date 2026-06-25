package ru.oparin.solution.service.campaign;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.oparin.solution.model.CampaignManagementState;
import ru.oparin.solution.model.CampaignScheduleSlot;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Optional;

/**
 * Правила, когда планировщику нужен опрос бюджета WB для РК.
 */
@Service
@RequiredArgsConstructor
public class CampaignBudgetPollEligibility {

    private static final ZoneId ZONE = ZoneId.of("Europe/Moscow");

    private final CampaignManageService manageService;

    /**
     * {@code true}, если в тике планировщика для РК допустим опрос бюджета (round-robin / trail).
     */
    public boolean needsBudgetPoll(CampaignManagementState state, Long advertId, Long cabinetId, ZonedDateTime now) {
        if (!state.isScheduleEnabled() || state.isManualStopped()) {
            return false;
        }
        if (isSlotBudgetCapPaused(state, advertId, cabinetId, now)) {
            return false;
        }
        LocalDateTime nowLocal = now.toLocalDateTime();
        if (manageService.findActiveSlotNow(advertId, cabinetId, now).isPresent()) {
            return true;
        }
        LocalDateTime trailUntil = state.getBudgetTrailUntil();
        return trailUntil != null && !nowLocal.isAfter(trailUntil);
    }

    /**
     * Лимит бюджета слота исчерпан, окно слота по расписанию ещё идёт — опрос не нужен.
     */
    public boolean isSlotBudgetCapPaused(
            CampaignManagementState state,
            Long advertId,
            Long cabinetId,
            ZonedDateTime now
    ) {
        Optional<CampaignScheduleSlot> activeSlot = manageService.findActiveSlotNow(advertId, cabinetId, now);
        return activeSlot.isPresent()
                && SlotBudgetSpendUtils.isSlotBudgetExhausted(state, activeSlot.get().getId());
    }

}
