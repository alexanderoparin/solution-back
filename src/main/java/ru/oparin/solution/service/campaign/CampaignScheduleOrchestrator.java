package ru.oparin.solution.service.campaign;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ru.oparin.solution.dto.wb.PromotionBudgetDepositRequest;
import ru.oparin.solution.model.*;
import ru.oparin.solution.repository.CampaignAutoBudgetSettingsRepository;
import ru.oparin.solution.repository.CampaignManagementStateRepository;
import ru.oparin.solution.repository.CampaignScheduleSlotRepository;
import ru.oparin.solution.repository.PromotionCampaignRepository;
import ru.oparin.solution.service.CabinetService;
import ru.oparin.solution.service.PromotionCampaignControlService;
import ru.oparin.solution.service.wb.WbPromotionApiClient;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Планировщик: расписание слотов, лимит бюджета слота, автопополнение.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class CampaignScheduleOrchestrator {

    private static final ZoneId ZONE = ZoneId.of("Europe/Moscow");

    private final CampaignManagementStateRepository stateRepository;
    private final CampaignScheduleSlotRepository slotRepository;
    private final CampaignAutoBudgetSettingsRepository autoBudgetRepository;
    private final PromotionCampaignRepository campaignRepository;
    private final CampaignManageService manageService;
    private final CampaignChangeLogService changeLogService;
    private final CampaignBudgetTimelineService timelineService;
    private final CampaignBudgetFetchService budgetFetchService;
    private final PromotionCampaignControlService controlService;
    private final CabinetService cabinetService;
    private final WbPromotionApiClient promotionApiClient;

    @Scheduled(cron = "0 * * * * *")
    @SchedulerLock(name = "campaignScheduleOrchestrator", lockAtLeastFor = "30s", lockAtMostFor = "55s")
    @Transactional
    public void tick() {
        List<CampaignManagementState> states = stateRepository.findAll();
        ZonedDateTime now = ZonedDateTime.now(ZONE);
        for (CampaignManagementState state : states) {
            if (!state.isScheduleEnabled()) {
                continue;
            }
            try {
                processCampaign(state, now);
            } catch (Exception e) {
                log.warn("Ошибка планировщика РК campaignId={} cabinetId={}: {}",
                        state.getCampaignId(), state.getCabinetId(), e.getMessage());
            }
        }
    }

    private void processCampaign(CampaignManagementState state, ZonedDateTime now) {
        Long advertId = state.getCampaignId();
        Long cabinetId = state.getCabinetId();
        Cabinet cabinet = cabinetService.findById(cabinetId).orElse(null);
        if (cabinet == null || cabinet.getApiKey() == null || cabinet.getApiKey().isBlank()) {
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
                checkSlotBudgetCap(state, slot, cabinet, campaign);
                if (!SlotBudgetSpendUtils.isSlotBudgetExhausted(state, slot.getId())) {
                    tryAutoTopUp(state, advertId, cabinetId, cabinet);
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
            Cabinet cabinet,
            PromotionCampaign campaign
    ) {
        if (state.getBudgetAtSlotStart() == null) {
            return;
        }
        Optional<Integer> budgetTotal = budgetFetchService.fetchBudgetTotal(cabinet, state.getCampaignId(), state);
        budgetTotal.ifPresent(total -> {
            int spent = SlotBudgetSpendUtils.computeSpentRub(state, total);
            if (spent >= slot.getBudgetRub()) {
                ensurePaused(cabinet, state.getCampaignId(), "РК остановлена: исчерпан бюджет слота");
                SlotBudgetSpendUtils.markSlotBudgetExhausted(state, slot.getId());
            }
        });
    }

    private void tryAutoTopUp(CampaignManagementState state, Long advertId, Long cabinetId, Cabinet cabinet) {
        autoBudgetRepository.findById(advertId).ifPresent(settings -> {
            if (!settings.isEnabled() || settings.getTopUpAmount() == null || settings.getThresholdRub() == null) {
                return;
            }
            LocalDate today = LocalDate.now(ZONE);
            if (state.getTopUpsTodayDate() != null && state.getTopUpsTodayDate().equals(today)) {
                if (settings.getMaxTopUpsPerDay() != null && state.getTopUpsTodayCount() >= settings.getMaxTopUpsPerDay()) {
                    return;
                }
            } else {
                state.setTopUpsTodayDate(today);
                state.setTopUpsTodayCount(0);
            }
            try {
                Optional<Integer> budgetTotal = budgetFetchService.fetchBudgetTotal(cabinet, advertId, state);
                if (budgetTotal.isEmpty() || budgetTotal.get() >= settings.getThresholdRub()) {
                    return;
                }
                PromotionBudgetDepositRequest req = PromotionBudgetDepositRequest.builder()
                        .sum(settings.getTopUpAmount())
                        .type(settings.getSourceType() != null ? settings.getSourceType() : 1)
                        .returnBudget(true)
                        .build();
                promotionApiClient.depositCampaignBudget(cabinet.getApiKey(), advertId, req);
                state.setTopUpsTodayCount(state.getTopUpsTodayCount() + 1);
                SlotBudgetSpendUtils.addSlotTopUp(state, settings.getTopUpAmount());
                changeLogService.log(advertId, cabinetId, null,
                        "Бюджет пополнен автоматически на " + settings.getTopUpAmount() + " ₽");
                Integer afterTopUp = budgetFetchService.fetchBudgetTotal(cabinet, advertId, state).orElse(null);
                timelineService.recordTopUp(advertId, cabinetId, settings.getTopUpAmount(), afterTopUp);
            } catch (Exception e) {
                log.warn("Автопополнение advertId={}: {}", advertId, e.getMessage());
            }
        });
    }

    private void ensureRunning(PromotionCampaign campaign, Cabinet cabinet, Long advertId) {
        if (campaign.getStatus() == CampaignStatus.ACTIVE) {
            return;
        }
        if (campaign.getStatus() == CampaignStatus.READY_TO_START || campaign.getStatus() == CampaignStatus.PAUSED) {
            try {
                controlService.enqueueStart(cabinet, advertId);
                changeLogService.log(advertId, cabinet.getId(), null, "РК запущена по расписанию");
                timelineService.recordStart(advertId, cabinet.getId());
            } catch (Exception e) {
                log.debug("Запуск по расписанию: {}", e.getMessage());
            }
        }
    }

    private void ensurePaused(Cabinet cabinet, Long advertId, String logMessage) {
        try {
            controlService.enqueuePause(cabinet, advertId);
            changeLogService.log(advertId, cabinet.getId(), null, logMessage);
            timelineService.recordStop(advertId, cabinet.getId());
        } catch (Exception e) {
            log.debug("Пауза по расписанию: {}", e.getMessage());
        }
    }
}
