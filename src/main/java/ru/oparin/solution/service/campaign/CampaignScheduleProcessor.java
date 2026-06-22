package ru.oparin.solution.service.campaign;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import ru.oparin.solution.dto.analytics.PromotionControlCapabilitiesDto;
import ru.oparin.solution.model.*;
import ru.oparin.solution.repository.CampaignManagementStateRepository;
import ru.oparin.solution.repository.PromotionCampaignRepository;
import ru.oparin.solution.service.CabinetService;
import ru.oparin.solution.service.PromotionCampaignControlService;
import ru.oparin.solution.service.PromotionCampaignControlWriteService;

import java.time.ZonedDateTime;
import java.util.Optional;

/**
 * Обработка одной РК в планировщике расписания — отдельная транзакция на кампанию.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CampaignScheduleProcessor {

    private final CampaignManagementStateRepository stateRepository;
    private final PromotionCampaignRepository campaignRepository;
    private final CampaignManageService manageService;
    private final CampaignChangeLogService changeLogService;
    private final CampaignBudgetTimelineService timelineService;
    private final CampaignBudgetFetchService budgetFetchService;
    private final CampaignAutoTopUpService autoTopUpService;
    private final PromotionCampaignControlService controlService;
    private final PromotionCampaignControlWriteService promotionControlWriteService;
    private final CabinetService cabinetService;
    private final CampaignManageAccessService campaignManageAccessService;

    /**
     * Тик планировщика для одной кампании; коммит независим от других кампаний.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void processCampaign(Long advertId, Long cabinetId, ZonedDateTime now) {
        CampaignManagementState state = stateRepository.findById(advertId).orElse(null);
        if (state == null || !state.isScheduleEnabled()) {
            return;
        }

        Cabinet cabinet = cabinetService.findById(cabinetId).orElse(null);
        if (cabinet == null || cabinet.getApiKey() == null || cabinet.getApiKey().isBlank()) {
            return;
        }
        if (!campaignManageAccessService.hasCampaignEntitlement(cabinet.getUser())) {
            manageService.stopScheduleDueToLostEntitlement(state, cabinet, cabinet.getUser());
            return;
        }

        PromotionCampaign campaign = campaignRepository.findByAdvertIdAndCabinet_Id(advertId, cabinetId).orElse(null);
        if (campaign == null || campaign.getStatus() == CampaignStatus.FINISHED) {
            return;
        }

        Optional<CampaignScheduleSlot> activeSlot = manageService.findActiveSlotNow(advertId, cabinetId, now);
        boolean inSlot = activeSlot.isPresent() && !state.isManualStopped();

        if (inSlot) {
            CampaignScheduleSlot slot = activeSlot.get();
            if (SlotBudgetSpendUtils.isSlotBudgetExhausted(state, slot.getId())) {
                if (campaign.getStatus() == CampaignStatus.ACTIVE) {
                    ensurePaused(cabinet, advertId, "РК остановлена: исчерпан бюджет слота");
                }
            } else {
                if (state.getActiveSlotId() == null || !state.getActiveSlotId().equals(slot.getId())) {
                    onSlotEnter(state, slot, cabinet);
                }
                checkSlotBudgetCap(state, slot, cabinet);
                if (!SlotBudgetSpendUtils.isSlotBudgetExhausted(state, slot.getId())) {
                    autoTopUpService.tryTopUpInNewTransaction(advertId, cabinetId, cabinet)
                            .ifPresent(amount -> reloadStateAfterTopUp(state, advertId, amount));
                    ensureRunning(campaign, cabinet, advertId);
                }
            }
        } else {
            if (state.getActiveSlotId() != null || state.getSlotBudgetExhaustedSlotId() != null) {
                onSlotLeave(state);
            }
            if (campaign.getStatus() == CampaignStatus.ACTIVE) {
                ensurePaused(cabinet, advertId, "РК остановлена по расписанию");
            }
        }
        stateRepository.save(state);
    }

    private void reloadStateAfterTopUp(CampaignManagementState state, Long advertId, int amount) {
        CampaignManagementState fresh = stateRepository.findById(advertId).orElse(null);
        if (fresh == null) {
            return;
        }
        state.setTopUpsTodayDate(fresh.getTopUpsTodayDate());
        state.setTopUpsTodayCount(fresh.getTopUpsTodayCount());
        state.setSlotTopUpsRub(fresh.getSlotTopUpsRub());
        state.setLastBudgetTotal(fresh.getLastBudgetTotal());
        state.setLastBudgetCheckedAt(fresh.getLastBudgetCheckedAt());
    }

    private void onSlotEnter(CampaignManagementState state, CampaignScheduleSlot slot, Cabinet cabinet) {
        budgetFetchService.fetchBudgetTotal(cabinet, state.getCampaignId(), state)
                .ifPresent(total -> SlotBudgetSpendUtils.beginSlotSession(state, slot.getId(), total));
    }

    private void onSlotLeave(CampaignManagementState state) {
        SlotBudgetSpendUtils.resetSlotSession(state);
    }

    private void checkSlotBudgetCap(
            CampaignManagementState state,
            CampaignScheduleSlot slot,
            Cabinet cabinet
    ) {
        if (state.getBudgetAtSlotStart() == null) {
            return;
        }
        budgetFetchService.fetchBudgetTotal(cabinet, state.getCampaignId(), state)
                .ifPresent(total -> {
                    int spent = SlotBudgetSpendUtils.computeSpentRub(state, total);
                    if (spent >= slot.getBudgetRub()) {
                        ensurePaused(cabinet, state.getCampaignId(), "РК остановлена: исчерпан бюджет слота");
                        SlotBudgetSpendUtils.markSlotBudgetExhausted(state, slot.getId());
                    }
                });
    }

    private void ensureRunning(PromotionCampaign campaign, Cabinet cabinet, Long advertId) {
        if (campaign.getStatus() == CampaignStatus.ACTIVE) {
            return;
        }
        if (!canControlPromotion(cabinet)) {
            return;
        }
        if (campaign.getStatus() == CampaignStatus.READY_TO_START || campaign.getStatus() == CampaignStatus.PAUSED) {
            try {
                controlService.enqueueStart(cabinet, advertId);
                changeLogService.log(advertId, cabinet.getId(), null, "РК запущена по расписанию");
                timelineService.recordStart(advertId, cabinet.getId());
            } catch (Exception e) {
                log.debug("Запуск по расписанию advertId={}: {}", advertId, e.getMessage());
            }
        }
    }

    private void ensurePaused(Cabinet cabinet, Long advertId, String logMessage) {
        if (!canControlPromotion(cabinet)) {
            log.debug("Пауза advertId={} пропущена: {}", advertId, controlBlockMessage(cabinet));
            return;
        }
        try {
            controlService.enqueuePause(cabinet, advertId);
            changeLogService.log(advertId, cabinet.getId(), null, logMessage);
            timelineService.recordStop(advertId, cabinet.getId());
        } catch (Exception e) {
            log.debug("Пауза по расписанию advertId={}: {}", advertId, e.getMessage());
        }
    }

    private boolean canControlPromotion(Cabinet cabinet) {
        return promotionControlWriteService.getCapabilities(cabinet).canControl();
    }

    private String controlBlockMessage(Cabinet cabinet) {
        PromotionControlCapabilitiesDto capabilities = promotionControlWriteService.getCapabilities(cabinet);
        return capabilities.message() != null
                ? capabilities.message()
                : PromotionCampaignControlWriteService.READ_ONLY_USER_MESSAGE;
    }
}
