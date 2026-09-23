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
 * Правила, когда планировщику нужен опрос бюджета / сверка статуса WB для РК.
 */
@Service
@RequiredArgsConstructor
public class WbCampaignBudgetPollEligibility {

    /** Интервал сверки статуса ACTIVE-РК с WB в активном слоте. */
    public static final int STATUS_RECONCILE_INTERVAL_MINUTES = 5;

    private final WbCampaignScheduleSlotRepository slotRepository;
    private final BidderStatusResolver bidderStatusResolver;

    /**
     * {@code true}, если в тике планировщика для РК нужен опрос бюджета (слот / trail).
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
     * {@code true}, если пора сверить статус РК с WB (активный слот, интервал истёк).
     */
    public boolean needsStatusReconcile(
            WbCampaignManagementState state,
            Long advertId,
            Long cabinetId,
            ZonedDateTime now
    ) {
        if (!state.isScheduleEnabled() || state.isManualStopped()) {
            return false;
        }
        if (findActiveSlotNow(advertId, cabinetId, now).isEmpty()) {
            return false;
        }
        LocalDateTime lastChecked = state.getLastStatusCheckedAt();
        if (lastChecked == null) {
            return true;
        }
        return !lastChecked.plusMinutes(STATUS_RECONCILE_INTERVAL_MINUTES).isAfter(now.toLocalDateTime());
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
