package ru.oparin.solution.service.campaign;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.oparin.solution.model.WbCampaignManagementState;
import ru.oparin.solution.model.WbCampaignScheduleSlot;
import ru.oparin.solution.repository.WbCampaignScheduleSlotRepository;

import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Правила, когда планировщику нужен опрос бюджета WB для РК.
 */
@Service
@RequiredArgsConstructor
public class WbCampaignBudgetPollEligibility {

    private final WbCampaignScheduleSlotRepository slotRepository;
    private final BidderStatusResolver bidderStatusResolver;

    /**
     * {@code true}, если в тике планировщика для РК допустим опрос бюджета (round-robin / trail).
     */
    public boolean needsBudgetPoll(WbCampaignManagementState state, Long advertId, Long cabinetId, ZonedDateTime now) {
        if (!state.isScheduleEnabled() || state.isManualStopped()) {
            return false;
        }
        if (isSlotBudgetCapPaused(state, advertId, cabinetId, now)) {
            return false;
        }
        LocalDateTime nowLocal = now.toLocalDateTime();
        if (findActiveSlotNow(advertId, cabinetId, now).isPresent()) {
            return true;
        }
        LocalDateTime trailUntil = state.getBudgetTrailUntil();
        return trailUntil != null && !nowLocal.isAfter(trailUntil);
    }

    /**
     * Лимит бюджета слота исчерпан, окно слота по расписанию ещё идёт — опрос не нужен.
     */
    public boolean isSlotBudgetCapPaused(
            WbCampaignManagementState state,
            Long advertId,
            Long cabinetId,
            ZonedDateTime now
    ) {
        Optional<WbCampaignScheduleSlot> activeSlot = findActiveSlotNow(advertId, cabinetId, now);
        return activeSlot.isPresent()
                && SlotBudgetSpendUtils.isSlotBudgetExhausted(state, activeSlot.get().getId());
    }

    private Optional<WbCampaignScheduleSlot> findActiveSlotNow(Long advertId, Long cabinetId, ZonedDateTime now) {
        List<WbCampaignScheduleSlot> slots = slotRepository
                .findByCampaignIdAndCabinetIdOrderByDayOfWeekAscStartTimeAsc(advertId, cabinetId);
        return bidderStatusResolver.findActiveSlotNow(slots, now);
    }
}
