package ru.oparin.solution.service.campaign;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.oparin.solution.model.*;
import ru.oparin.solution.repository.CabinetRepository;

import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;

/**
 * Вычисление статуса биддера по состоянию расписания, слотам и статусу WB.
 */
@Service
@RequiredArgsConstructor
public class BidderStatusResolver {

    private static final ZoneId SCHEDULE_ZONE = ZoneId.of("Europe/Moscow");

    private final WbCampaignManageAccessService campaignManageAccessService;
    private final CabinetRepository cabinetRepository;

    /**
     * Статус биддера для одной кампании.
     */
    public BidderStatus resolve(
            WbCampaignManagementState state,
            WbPromotionCampaign campaign,
            Long advertId,
            Long cabinetId,
            List<WbCampaignScheduleSlot> slotsForCampaign,
            User seller
    ) {
        WbCampaignManagementState effectiveState = state != null
                ? state
                : defaultState(advertId, cabinetId);
        if (effectiveState.isManualStopped()) {
            return BidderStatus.OFF;
        }
        Cabinet cabinet = cabinetId != null
                ? cabinetRepository.findByIdWithUser(cabinetId).orElse(null)
                : null;
        if (cabinet == null || !campaignManageAccessService.hasCampaignEntitlement(cabinet)) {
            return BidderStatus.NO_ACCESS;
        }
        if (slotsForCampaign == null || slotsForCampaign.isEmpty()) {
            return BidderStatus.NO_SLOTS;
        }
        ZonedDateTime now = ZonedDateTime.now(SCHEDULE_ZONE);
        Optional<WbCampaignScheduleSlot> activeSlot = findActiveSlotNow(slotsForCampaign, now);
        if (activeSlot.isEmpty()) {
            return BidderStatus.WAITING;
        }
        if (SlotBudgetSpendUtils.isSlotBudgetExhausted(effectiveState, activeSlot.get().getId())) {
            return BidderStatus.SLOT_LIMIT;
        }
        boolean wbActive = campaign != null && campaign.getStatus() == WbCampaignStatus.ACTIVE;
        if (wbActive) {
            return BidderStatus.RUNNING;
        }
        return BidderStatus.WAITING;
    }

    /**
     * Пакетный расчёт статусов биддера для кампаний кабинета.
     */
    public Map<Long, BidderStatus> resolveForCabinet(
            Long cabinetId,
            User seller,
            List<WbPromotionCampaign> campaigns,
            Map<Long, WbCampaignManagementState> statesByCampaignId,
            Map<Long, List<WbCampaignScheduleSlot>> slotsByCampaignId
    ) {
        if (cabinetId == null || campaigns == null || campaigns.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Long, BidderStatus> result = new HashMap<>();
        for (WbPromotionCampaign campaign : campaigns) {
            Long advertId = campaign.getAdvertId();
            result.put(
                    advertId,
                    resolve(
                            statesByCampaignId.get(advertId),
                            campaign,
                            advertId,
                            cabinetId,
                            slotsByCampaignId.getOrDefault(advertId, List.of()),
                            seller
                    )
            );
        }
        return result;
    }

    public Optional<WbCampaignScheduleSlot> findActiveSlotNow(List<WbCampaignScheduleSlot> slots, ZonedDateTime now) {
        if (slots == null || slots.isEmpty()) {
            return Optional.empty();
        }
        short dow = (short) now.getDayOfWeek().getValue();
        LocalTime time = WbCampaignSlotTimeUtils.snap(now.toLocalTime());
        return slots.stream()
                .filter(s -> s.getDayOfWeek() == dow)
                .filter(s -> WbCampaignSlotTimeUtils.containsTime(time, s.getStartTime(), s.getEndTime()))
                .findFirst();
    }

    private static WbCampaignManagementState defaultState(Long advertId, Long cabinetId) {
        return WbCampaignManagementState.builder()
                .campaignId(advertId)
                .cabinetId(cabinetId)
                .manualStopped(true)
                .scheduleEnabled(true)
                .topUpsTodayCount(0)
                .build();
    }
}
