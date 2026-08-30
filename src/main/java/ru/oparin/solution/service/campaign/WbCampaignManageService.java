package ru.oparin.solution.service.campaign;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.oparin.solution.dto.analytics.CampaignControlEnqueueResponse;
import ru.oparin.solution.dto.analytics.manage.*;
import ru.oparin.solution.dto.wb.WbPromotionBudgetDepositRequest;
import ru.oparin.solution.dto.wb.WbPromotionBudgetResponse;
import ru.oparin.solution.model.*;
import ru.oparin.solution.repository.WbCampaignAutoBudgetSettingsRepository;
import ru.oparin.solution.repository.WbCampaignManagementStateRepository;
import ru.oparin.solution.repository.WbCampaignScheduleSlotRepository;
import ru.oparin.solution.repository.WbPromotionCampaignRepository;
import ru.oparin.solution.service.AnalyticsService;
import ru.oparin.solution.service.CabinetService;
import ru.oparin.solution.service.WbPromotionCampaignControlService;
import ru.oparin.solution.service.WbPromotionCampaignControlWriteService;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;

/**
 * Управление рекламной кампанией: автопополнение, расписание, журнал.
 */
@Service
@RequiredArgsConstructor
public class WbCampaignManageService {

    private static final ZoneId SCHEDULE_ZONE = ZoneId.of("Europe/Moscow");
    /** Минимальная сумма пополнения бюджета РК на Wildberries, ₽. */
    private static final int MIN_TOP_UP_AMOUNT_RUB = 1000;
    private static final String SCHEDULE_STOPPED_READ_ONLY =
            "Расписание отключено: " + WbPromotionCampaignControlWriteService.READ_ONLY_USER_MESSAGE;
    private final AnalyticsService analyticsService;
    private final WbPromotionCampaignRepository campaignRepository;
    private final WbCampaignAutoBudgetSettingsRepository autoBudgetRepository;
    private final WbCampaignScheduleSlotRepository slotRepository;
    private final WbCampaignManagementStateRepository stateRepository;
    private final WbCampaignChangeLogService changeLogService;
    private final WbPromotionCampaignControlService controlService;
    private final WbPromotionCampaignControlWriteService controlWriteService;
    private final CabinetService cabinetService;
    private final WbCampaignBudgetDepositService budgetDepositService;
    private final WbCabinetPromotionBalanceCacheService balanceCacheService;
    private final WbCampaignBudgetTimelineService timelineService;
    private final WbCampaignBudgetFetchService budgetFetchService;
    private final WbCampaignBudgetChartService budgetChartService;
    private final WbCampaignManageAccessService campaignManageAccessService;
    private final BidderStatusResolver bidderStatusResolver;
    private final WbCampaignBudgetTrailService budgetTrailService;
    private final WbCampaignStartBudgetGuard startBudgetGuard;

    @Transactional
    public CampaignManageResponseDto getManage(Long advertId, Long cabinetId, User seller) {
        syncScheduleOffIfControlBlocked(advertId, cabinetId);
        var detail = analyticsService.getCampaignDetail(advertId, cabinetId, seller != null ? seller.getId() : null);
        if (detail == null) {
            return null;
        }
        WbCampaignManagementState state = stateOrDefaults(advertId, cabinetId);
        WbPromotionCampaign campaign = campaignRepository.findByAdvertIdAndCabinet_Id(advertId, cabinetId).orElse(null);
        List<WbCampaignScheduleSlot> slots = slotRepository
                .findByCampaignIdAndCabinetIdOrderByDayOfWeekAscStartTimeAsc(advertId, cabinetId);
        BidderStatus bidderStatus = bidderStatusResolver.resolve(state, campaign, advertId, cabinetId, slots, seller);
        return CampaignManageResponseDto.builder()
                .id(detail.getId())
                .name(detail.getName())
                .status(detail.getStatus())
                .statusName(detail.getStatus() != null && detail.getStatus() == 9 ? "активна" : "приостановлена")
                .articlesCount(detail.getArticlesCount())
                .articles(detail.getArticles())
                .bidderStatus(bidderStatus.name())
                .scheduleEnabled(!state.isManualStopped())
                .autoBudget(mapAutoBudget(autoBudgetOrDefaults(advertId, cabinetId)))
                .slots(loadSlots(advertId, cabinetId))
                .build();
    }

    @Transactional
    public CampaignAutoBudgetDto saveAutoBudget(
            Long advertId, Long cabinetId, User user, CampaignAutoBudgetRequestDto request
    ) {
        ensureCampaign(advertId, cabinetId);
        controlWriteService.ensureControlAllowed(cabinetService.findById(cabinetId)
                .orElseThrow(() -> new IllegalArgumentException("Кабинет не найден")));
        WbCampaignAutoBudgetSettings settings = getOrCreateAutoBudget(advertId, cabinetId);
        validateAutoBudgetTopUpAmount(request.getTopUpAmount());
        settings.setEnabled(request.isEnabled());
        settings.setTopUpAmount(request.getTopUpAmount());
        settings.setSourceType(request.getSourceType());
        settings.setUsePromoCashback(request.getUsePromoCashback() == null || request.getUsePromoCashback());
        settings.setThresholdRub(request.getThresholdRub());
        settings.setMaxTopUpsPerDay(request.getMaxTopUpsPerDay());
        settings.setLocked(true);
        autoBudgetRepository.save(settings);
        changeLogService.log(advertId, cabinetId, user, "Сохранены настройки автопополнения бюджета");
        return mapAutoBudget(settings);
    }

    /**
     * Включает или выключает автопополнение без изменения суммы, источника и порогов.
     */
    @Transactional
    public CampaignAutoBudgetDto setAutoBudgetEnabled(
            Long advertId,
            Long cabinetId,
            User user,
            boolean enabled
    ) {
        ensureCampaign(advertId, cabinetId);
        controlWriteService.ensureControlAllowed(cabinetService.findById(cabinetId)
                .orElseThrow(() -> new IllegalArgumentException("Кабинет не найден")));
        WbCampaignAutoBudgetSettings settings = getOrCreateAutoBudget(advertId, cabinetId);
        if (settings.isEnabled() != enabled) {
            settings.setEnabled(enabled);
            autoBudgetRepository.save(settings);
            changeLogService.log(
                    advertId,
                    cabinetId,
                    user,
                    enabled ? "Автопополнение бюджета включено" : "Автопополнение бюджета выключено"
            );
        }
        return mapAutoBudget(settings);
    }

    @Transactional
    public CampaignAutoBudgetDto unlockAutoBudget(Long advertId, Long cabinetId, User user) {
        ensureCampaign(advertId, cabinetId);
        WbCampaignAutoBudgetSettings settings = getOrCreateAutoBudget(advertId, cabinetId);
        settings.setLocked(false);
        autoBudgetRepository.save(settings);
        changeLogService.log(advertId, cabinetId, user, "Редактирование настроек автопополнения бюджета");
        return mapAutoBudget(settings);
    }

    /**
     * Единоразовое пополнение бюджета РК через WB API.
     */
    @Transactional
    public CampaignManualTopUpResponseDto manualTopUp(
            Long advertId,
            Long cabinetId,
            User user,
            CampaignManualTopUpRequestDto request
    ) {
        ensureCampaign(advertId, cabinetId);
        Cabinet cabinet = cabinetService.findById(cabinetId)
                .orElseThrow(() -> new IllegalArgumentException("Кабинет не найден"));
        controlWriteService.ensureControlAllowed(cabinet);
        validateAutoBudgetTopUpAmount(request.getTopUpAmount());
        if (request.getSourceType() == null) {
            throw new IllegalArgumentException("Укажите источник пополнения");
        }

        WbCampaignManagementState state = getOrCreateState(advertId, cabinetId);
        int topUpAmount = request.getTopUpAmount();
        int budgetBeforeTopUp = budgetFetchService.fetchBudgetForDecision(cabinet, advertId, state).orElse(0);

        WbPromotionBudgetDepositRequest depositRequest = WbPromotionBudgetDepositRequest.builder()
                .sum(topUpAmount)
                .type(request.getSourceType())
                .returnBudget(true)
                .build();
        boolean usePromo = request.getUsePromoCashback() == null || request.getUsePromoCashback();
        WbPromotionBudgetResponse depositResponse;
        try {
            depositResponse = budgetDepositService.depositWithPromoFallback(
                    cabinet, advertId, depositRequest, usePromo).getResponse();
        } catch (Exception e) {
            throw new IllegalArgumentException(
                    e.getMessage() != null ? e.getMessage() : "Не удалось пополнить бюджет кампании");
        }

        int budgetAfterTopUp = budgetFetchService.resolveBudgetAfterTopUp(
                budgetBeforeTopUp, topUpAmount, depositResponse);
        budgetFetchService.storeBudgetTotal(state, advertId, cabinetId, budgetAfterTopUp);
        startBudgetGuard.clearBlockIfBudgetAvailable(state, budgetAfterTopUp);
        SlotBudgetSpendUtils.addSlotTopUp(state, topUpAmount);
        stateRepository.save(state);

        String cashbackNote = depositRequest.getCashbackSum() != null && depositRequest.getCashbackSum() > 0
                ? (", из них промо " + depositRequest.getCashbackSum() + " ₽ до "
                + depositRequest.getCashbackPercent() + "%")
                : "";
        changeLogService.log(
                advertId,
                cabinetId,
                user,
                "Бюджет пополнен вручную на " + topUpAmount + " ₽ ("
                        + budgetBeforeTopUp + " ₽ -> " + budgetAfterTopUp + " ₽)" + cashbackNote
        );
        timelineService.recordTopUp(advertId, cabinetId, topUpAmount, budgetAfterTopUp);

        return CampaignManualTopUpResponseDto.builder()
                .topUpAmount(topUpAmount)
                .budgetAfterTopUp(budgetAfterTopUp)
                .message("Бюджет пополнен на " + topUpAmount + " ₽")
                .build();
    }

    @Transactional
    public List<WbCampaignScheduleSlotDto> createSlots(
            Long advertId, Long cabinetId, User user, WbCampaignScheduleSlotRequestDto request
    ) {
        ensureCampaign(advertId, cabinetId);
        LocalTime start = WbCampaignSlotTimeUtils.parseStartHHmm(request.getStartTime());
        LocalTime end = WbCampaignSlotTimeUtils.parseEndHHmm(request.getEndTime());
        if (!WbCampaignSlotTimeUtils.isEndAfterStart(start, end)) {
            throw new IllegalArgumentException("Время окончания должно быть позже начала");
        }
        if (request.getBudgetRub() == null || request.getBudgetRub() <= 0) {
            throw new IllegalArgumentException("Укажите бюджет слота");
        }
        UUID groupId = request.isRepeat() ? UUID.randomUUID() : null;
        WbCampaignSlotRepeatMode mode = request.isRepeat()
                ? parseRepeatMode(request.getRepeatMode())
                : WbCampaignSlotRepeatMode.DAILY;
        List<Short> days = resolveDays(request.getDayOfWeek(), mode, request.isRepeat());
        for (Short day : days) {
            ensureNoSlotOverlap(advertId, cabinetId, day, start, end, null);
        }
        List<WbCampaignScheduleSlot> created = new ArrayList<>();
        for (Short day : days) {
            WbCampaignScheduleSlot slot = WbCampaignScheduleSlot.builder()
                    .campaignId(advertId)
                    .cabinetId(cabinetId)
                    .dayOfWeek(day)
                    .startTime(start)
                    .endTime(end)
                    .budgetRub(request.getBudgetRub())
                    .repeatGroupId(groupId)
                    .repeatMode(mode)
                    .build();
            created.add(slotRepository.save(slot));
        }
        String scheduleLabel = formatScheduleLabel(request.isRepeat(), request.getDayOfWeek(), mode);
        changeLogService.log(advertId, cabinetId, user,
                "Добавлен слот «" + scheduleLabel + " " + formatSlotRange(start, end) + "»");
        applySlotEditPolicy(advertId, cabinetId, stateRepository.findById(advertId).orElse(null));
        return created.stream().map(this::mapSlot).toList();
    }

    @Transactional
    public WbCampaignScheduleSlotDto updateSlot(
            Long advertId, Long cabinetId, Long slotId, User user, WbCampaignScheduleSlotUpdateDto request
    ) {
        ensureCampaign(advertId, cabinetId);
        WbCampaignScheduleSlot slot = slotRepository.findById(slotId)
                .orElseThrow(() -> new IllegalArgumentException("Слот не найден"));
        if (!slot.getCampaignId().equals(advertId) || !slot.getCabinetId().equals(cabinetId)) {
            throw new IllegalArgumentException("Слот не принадлежит этой кампании");
        }
        String oldTime = formatSlotRange(slot.getStartTime(), slot.getEndTime());
        Integer oldBudget = slot.getBudgetRub();
        LocalTime oldEnd = slot.getEndTime();
        if (request.getStartTime() != null) {
            slot.setStartTime(WbCampaignSlotTimeUtils.parseStartHHmm(request.getStartTime()));
        }
        if (request.getEndTime() != null) {
            slot.setEndTime(WbCampaignSlotTimeUtils.parseEndHHmm(request.getEndTime()));
        }
        if (!WbCampaignSlotTimeUtils.isEndAfterStart(slot.getStartTime(), slot.getEndTime())) {
            throw new IllegalArgumentException("Время окончания должно быть позже начала");
        }
        if (request.getBudgetRub() != null) {
            slot.setBudgetRub(request.getBudgetRub());
        }
        ensureNoSlotOverlap(advertId, cabinetId, slot.getDayOfWeek(), slot.getStartTime(), slot.getEndTime(), slot.getId());
        slotRepository.save(slot);
        WbCampaignManagementState state = stateRepository.findById(advertId).orElse(null);
        if (request.getBudgetRub() != null && !Objects.equals(oldBudget, request.getBudgetRub())) {
            changeLogService.log(advertId, cabinetId, user,
                    "Изменен бюджет «" + dayName(slot.getDayOfWeek()) + ", было " + oldBudget
                            + ", стало " + request.getBudgetRub() + "»");
        }
        if (request.getStartTime() != null || request.getEndTime() != null) {
            String newTime = formatSlotRange(slot.getStartTime(), slot.getEndTime());
            if (!oldTime.equals(newTime)) {
                changeLogService.log(advertId, cabinetId, user,
                        "Изменено время «" + dayName(slot.getDayOfWeek()) + ", было " + oldTime
                                + ", стало " + newTime + "»");
            }
        }
        applySlotEditPolicyAfterUpdate(advertId, cabinetId, state, slot, oldEnd, oldBudget, request);
        return mapSlot(slot);
    }

    @Transactional
    public void deleteSlot(Long advertId, Long cabinetId, Long slotId, User user, boolean deleteAll) {
        ensureCampaign(advertId, cabinetId);
        if (deleteAll) {
            deleteAllSlots(advertId, cabinetId, user);
            return;
        }
        WbCampaignScheduleSlot slot = slotRepository.findById(slotId)
                .orElseThrow(() -> new IllegalArgumentException("Слот не найден"));
        if (!advertId.equals(slot.getCampaignId()) || !cabinetId.equals(slot.getCabinetId())) {
            throw new IllegalArgumentException("Слот не найден");
        }
        String msg = "Удален слот «" + dayName(slot.getDayOfWeek()) + " "
                + formatSlotRange(slot.getStartTime(), slot.getEndTime()) + "»";
        slotRepository.delete(slot);
        changeLogService.log(advertId, cabinetId, user, msg);
        applySlotEditPolicy(advertId, cabinetId, stateRepository.findById(advertId).orElse(null));
    }

    private void deleteAllSlots(Long advertId, Long cabinetId, User user) {
        List<WbCampaignScheduleSlot> slots = slotRepository
                .findByCampaignIdAndCabinetIdOrderByDayOfWeekAscStartTimeAsc(advertId, cabinetId);
        if (slots.isEmpty()) {
            return;
        }
        int count = slots.size();
        slotRepository.deleteByCampaignIdAndCabinetId(advertId, cabinetId);
        WbCampaignManagementState state = stateRepository.findById(advertId).orElse(null);
        if (state != null) {
            state.setActiveSlotId(null);
            state.setBudgetAtSlotStart(null);
            state.setSlotBudgetExhaustedSlotId(null);
            state.setSlotTopUpsRub(0);
            stateRepository.save(state);
        }
        changeLogService.log(advertId, cabinetId, user, "Удалено расписание показов (" + count + " слотов)");
        applySlotEditPolicy(advertId, cabinetId, state);
    }

    @Transactional
    public CampaignControlEnqueueResponse manualStart(Long advertId, Long cabinetId, User user) {
        Cabinet cabinet = cabinetService.findById(cabinetId)
                .orElseThrow(() -> new IllegalArgumentException("Кабинет не найден"));
        WbCampaignManagementState state = getOrCreateState(advertId, cabinetId);
        state.setManualStopped(false);
        stateRepository.save(state);
        changeLogService.log(advertId, cabinetId, user, "Расписание включено");

        ZonedDateTime now = ZonedDateTime.now(SCHEDULE_ZONE);
        if (!findActiveSlotNow(advertId, cabinetId, now).isPresent()) {
            return new CampaignControlEnqueueResponse(false, null, "Расписание включено");
        }

        WbPromotionCampaign campaign = campaignRepository.findByAdvertIdAndCabinet_Id(advertId, cabinetId).orElse(null);
        if (campaign != null && campaign.getStatus() == WbCampaignStatus.ACTIVE) {
            budgetTrailService.clearTrail(state);
            timelineService.recordStart(advertId, cabinetId);
            return new CampaignControlEnqueueResponse(false, null, "Расписание включено");
        }
        if (campaign != null && campaign.getStatus() == WbCampaignStatus.FINISHED) {
            throw new IllegalArgumentException(
                    "Нельзя запустить РК на WB: кампания завершена. Измените статус в кабинете WB или создайте новую РК.");
        }

        budgetTrailService.clearTrail(state);
        timelineService.recordStart(advertId, cabinetId);
        return controlService.enqueueStart(cabinet, advertId);
    }

    @Transactional
    public CampaignControlEnqueueResponse manualPause(Long advertId, Long cabinetId, User user) {
        Cabinet cabinet = cabinetService.findById(cabinetId)
                .orElseThrow(() -> new IllegalArgumentException("Кабинет не найден"));
        WbCampaignManagementState state = getOrCreateState(advertId, cabinetId);
        state.setManualStopped(true);
        state.setActiveSlotId(null);
        state.setBudgetAtSlotStart(null);
        stateRepository.save(state);
        changeLogService.log(advertId, cabinetId, user, "Расписание выключено");

        WbPromotionCampaign campaign = campaignRepository.findByAdvertIdAndCabinet_Id(advertId, cabinetId).orElse(null);
        if (campaign != null && campaign.getStatus() == WbCampaignStatus.ACTIVE) {
            budgetTrailService.beginTrail(state);
            return controlService.enqueuePause(cabinet, advertId);
        }
        return new CampaignControlEnqueueResponse(false, null, "Расписание выключено");
    }

    /**
     * Останавливает активное расписание при потере entitlement (истечение подписки).
     * Идемпотентно: при уже остановленном расписании ничего не делает.
     */
    @Transactional
    public void stopScheduleDueToLostEntitlement(
            WbCampaignManagementState state,
            Cabinet cabinet,
            User seller
    ) {
        if (state == null || state.isManualStopped() || !state.isScheduleEnabled()) {
            return;
        }
        Long advertId = state.getCampaignId();
        Long cabinetId = cabinet.getId();
        WbPromotionCampaign campaign = campaignRepository.findByAdvertIdAndCabinet_Id(advertId, cabinetId).orElse(null);
        boolean campaignWasActive = campaign != null && campaign.getStatus() == WbCampaignStatus.ACTIVE;

        if (campaignWasActive && controlWriteService.getCapabilities(cabinet).canControl()) {
            try {
                controlService.enqueuePause(cabinet, advertId);
                budgetTrailService.beginTrail(state);
            } catch (Exception ignored) {
                return;
            }
        }

        String message = campaignManageAccessService.scheduleStopMessageForCabinet(cabinet);
        if (campaignWasActive) {
            message = message + " Активная РК остановлена.";
        }
        state.setManualStopped(true);
        SlotBudgetSpendUtils.resetSlotSession(state);
        changeLogService.log(advertId, cabinetId, null, message);
        stateRepository.save(state);
    }

    /**
     * Отключает расписание по всем РК кабинета после обнаружения read-only токена WB.
     */
    @Transactional
    public void stopAllSchedulesDueToReadOnlyToken(Long cabinetId) {
        if (cabinetId == null) {
            return;
        }
        for (WbCampaignManagementState state : stateRepository.findByCabinetId(cabinetId)) {
            disableScheduleLocally(state, SCHEDULE_STOPPED_READ_ONLY);
        }
    }

    /**
     * Синхронизирует UI/БД: при активной блокировке записи расписание должно быть выключено.
     */
    @Transactional
    public void syncScheduleOffIfControlBlocked(Long advertId, Long cabinetId) {
        WbCampaignManagementState state = stateRepository.findById(advertId).orElse(null);
        if (state == null || state.isManualStopped()) {
            return;
        }
        Cabinet cabinet = cabinetService.findById(cabinetId).orElse(null);
        if (cabinet == null || controlWriteService.getCapabilities(cabinet).canControl()) {
            return;
        }
        disableScheduleLocally(state, SCHEDULE_STOPPED_READ_ONLY);
    }

    private void disableScheduleLocally(WbCampaignManagementState state, String logMessage) {
        if (state.isManualStopped()) {
            return;
        }
        state.setManualStopped(true);
        state.setActiveSlotId(null);
        state.setBudgetAtSlotStart(null);
        SlotBudgetSpendUtils.resetSlotSession(state);
        changeLogService.log(state.getCampaignId(), state.getCabinetId(), null, logMessage);
        stateRepository.save(state);
    }

    @Transactional(readOnly = true)
    public BalanceSourcesResponseDto balanceSources(Long cabinetId) {
        return balanceCacheService.getBalanceSources(cabinetId, true);
    }

    public BalanceRefreshResponseDto refreshBalanceSources(Long cabinetId) {
        return balanceCacheService.refreshBalance(cabinetId);
    }

    @Transactional(readOnly = true)
    public CampaignBudgetChartDto budgetChart(
            Long advertId,
            Long cabinetId,
            Integer hours,
            Integer stepHours,
            LocalDateTime from,
            LocalDateTime to
    ) {
        ensureCampaign(advertId, cabinetId);
        return budgetChartService.buildChart(advertId, cabinetId, hours, stepHours, from, to);
    }

    @Transactional(readOnly = true)
    public Page<WbCampaignChangeLogEntryDto> changeLogPage(Long advertId, Long cabinetId, int page, int size) {
        return changeLogService.page(advertId, cabinetId, page, size);
    }

    public Optional<WbCampaignScheduleSlot> findActiveSlotNow(Long advertId, Long cabinetId, ZonedDateTime now) {
        List<WbCampaignScheduleSlot> slots = slotRepository
                .findByCampaignIdAndCabinetIdOrderByDayOfWeekAscStartTimeAsc(advertId, cabinetId);
        return bidderStatusResolver.findActiveSlotNow(slots, now);
    }

    private void applySlotEditPolicy(Long advertId, Long cabinetId, WbCampaignManagementState state) {
        if (state == null || state.isManualStopped()) {
            return;
        }
        boolean inSlot = findActiveSlotNow(advertId, cabinetId, ZonedDateTime.now(SCHEDULE_ZONE)).isPresent();
        if (!inSlot) {
            pauseIfActive(advertId, cabinetId, state, "РК остановлена из-за изменения расписания");
        }
    }

    private void applySlotEditPolicyAfterUpdate(
            Long advertId,
            Long cabinetId,
            WbCampaignManagementState state,
            WbCampaignScheduleSlot slot,
            LocalTime oldEnd,
            Integer oldBudget,
            WbCampaignScheduleSlotUpdateDto request
    ) {
        if (state == null || state.isManualStopped()) {
            return;
        }
        ZonedDateTime now = ZonedDateTime.now(SCHEDULE_ZONE);
        Optional<WbCampaignScheduleSlot> active = findActiveSlotNow(advertId, cabinetId, now);
        if (active.isEmpty() || !active.get().getId().equals(slot.getId())) {
            applySlotEditPolicy(advertId, cabinetId, state);
            return;
        }
        LocalTime nowTime = WbCampaignSlotTimeUtils.snap(now.toLocalTime());
        if (request.getEndTime() != null && !oldEnd.equals(slot.getEndTime())) {
            if (WbCampaignSlotTimeUtils.toMinutes(nowTime) >= WbCampaignSlotTimeUtils.endMinutes(
                    slot.getStartTime(), slot.getEndTime())) {
                pauseIfActive(advertId, cabinetId, state, "РК остановлена: время слота сокращено до текущего момента");
            }
        }
        if (request.getBudgetRub() != null && oldBudget != null && request.getBudgetRub() < oldBudget) {
            checkBudgetDecreasePause(advertId, cabinetId, state, slot.getBudgetRub());
        }
    }

    private void checkBudgetDecreasePause(Long advertId, Long cabinetId, WbCampaignManagementState state, int newBudgetRub) {
        if (state.getBudgetAtSlotStart() == null) {
            return;
        }
        Cabinet cabinet = cabinetService.findById(cabinetId).orElse(null);
        if (cabinet == null || cabinet.getApiKey() == null || cabinet.getApiKey().isBlank()) {
            return;
        }
        Optional<Integer> budgetTotal = budgetFetchService.fetchBudgetTotal(cabinet, advertId, state);
        budgetTotal.ifPresent(total -> {
            int spent = SlotBudgetSpendUtils.computeSpentRub(state, total);
            if (spent >= newBudgetRub) {
                pauseIfActive(advertId, cabinetId, state, "РК остановлена: исчерпан новый лимит бюджета слота");
            }
        });
    }

    private void pauseIfActive(Long advertId, Long cabinetId, WbCampaignManagementState state, String logMessage) {
        WbPromotionCampaign campaign = campaignRepository.findByAdvertIdAndCabinet_Id(advertId, cabinetId).orElse(null);
        if (campaign != null && campaign.getStatus() == WbCampaignStatus.ACTIVE) {
            try {
                Cabinet cabinet = cabinetService.findById(cabinetId).orElse(null);
                if (cabinet != null) {
                    controlService.enqueuePause(cabinet, advertId);
                    changeLogService.log(advertId, cabinetId, null, logMessage);
                    if (state != null) {
                        budgetTrailService.beginTrail(state);
                    }
                    if (state != null && state.getActiveSlotId() != null) {
                        SlotBudgetSpendUtils.markSlotBudgetExhausted(state, state.getActiveSlotId());
                        stateRepository.save(state);
                    }
                }
            } catch (Exception ignored) {
                // rate limit — scheduler retry
            }
        }
    }

    private WbCampaignManagementState stateOrDefaults(Long advertId, Long cabinetId) {
        return stateRepository.findById(advertId)
                .orElseGet(() -> WbCampaignManagementState.builder()
                        .campaignId(advertId)
                        .cabinetId(cabinetId)
                        .manualStopped(true)
                        .scheduleEnabled(true)
                        .topUpsTodayCount(0)
                        .build());
    }

    private WbCampaignAutoBudgetSettings autoBudgetOrDefaults(Long advertId, Long cabinetId) {
        return autoBudgetRepository.findById(advertId)
                .orElseGet(() -> WbCampaignAutoBudgetSettings.builder()
                        .campaignId(advertId)
                        .cabinetId(cabinetId)
                        .enabled(false)
                        .usePromoCashback(true)
                        .locked(false)
                        .build());
    }

    private WbCampaignManagementState getOrCreateState(Long advertId, Long cabinetId) {
        ensureCampaign(advertId, cabinetId);
        return stateRepository.findById(advertId)
                .orElseGet(() -> stateRepository.save(WbCampaignManagementState.builder()
                        .campaignId(advertId)
                        .cabinetId(cabinetId)
                        .manualStopped(true)
                        .scheduleEnabled(true)
                        .topUpsTodayCount(0)
                        .build()));
    }

    private WbCampaignAutoBudgetSettings getOrCreateAutoBudget(Long advertId, Long cabinetId) {
        ensureCampaign(advertId, cabinetId);
        return autoBudgetRepository.findById(advertId)
                .orElseGet(() -> autoBudgetRepository.save(WbCampaignAutoBudgetSettings.builder()
                        .campaignId(advertId)
                        .cabinetId(cabinetId)
                        .enabled(false)
                        .usePromoCashback(true)
                        .locked(false)
                        .build()));
    }

    private void ensureCampaign(Long advertId, Long cabinetId) {
        if (!campaignRepository.findByAdvertIdAndCabinet_Id(advertId, cabinetId).isPresent()) {
            throw new IllegalArgumentException("Кампания не найдена в этом кабинете");
        }
    }

    private void validateAutoBudgetTopUpAmount(Integer topUpAmount) {
        if (topUpAmount != null && topUpAmount < MIN_TOP_UP_AMOUNT_RUB) {
            throw new IllegalArgumentException(
                    "Минимальная сумма пополнения — " + MIN_TOP_UP_AMOUNT_RUB + " ₽");
        }
    }

    private List<WbCampaignScheduleSlotDto> loadSlots(Long advertId, Long cabinetId) {
        return slotRepository.findByCampaignIdAndCabinetIdOrderByDayOfWeekAscStartTimeAsc(advertId, cabinetId).stream()
                .map(this::mapSlot)
                .toList();
    }

    private CampaignAutoBudgetDto mapAutoBudget(WbCampaignAutoBudgetSettings s) {
        return CampaignAutoBudgetDto.builder()
                .enabled(s.isEnabled())
                .topUpAmount(s.getTopUpAmount())
                .sourceType(s.getSourceType())
                .usePromoCashback(s.isUsePromoCashback())
                .thresholdRub(s.getThresholdRub())
                .maxTopUpsPerDay(s.getMaxTopUpsPerDay())
                .locked(s.isLocked())
                .build();
    }

    private WbCampaignScheduleSlotDto mapSlot(WbCampaignScheduleSlot s) {
        return WbCampaignScheduleSlotDto.builder()
                .id(s.getId())
                .dayOfWeek(s.getDayOfWeek())
                .startTime(WbCampaignSlotTimeUtils.format(s.getStartTime()))
                .endTime(WbCampaignSlotTimeUtils.formatEnd(s.getStartTime(), s.getEndTime()))
                .budgetRub(s.getBudgetRub())
                .repeatGroupId(s.getRepeatGroupId())
                .repeatMode(s.getRepeatMode() != null ? s.getRepeatMode().name() : null)
                .build();
    }

    private static WbCampaignSlotRepeatMode parseRepeatMode(String mode) {
        if (mode == null || mode.isBlank()) {
            return WbCampaignSlotRepeatMode.DAILY;
        }
        return WbCampaignSlotRepeatMode.valueOf(mode.trim().toUpperCase());
    }

    private void ensureNoSlotOverlap(
            Long advertId,
            Long cabinetId,
            short dayOfWeek,
            LocalTime start,
            LocalTime end,
            Long excludeSlotId
    ) {
        List<WbCampaignScheduleSlot> onDay = slotRepository
                .findByCampaignIdAndCabinetIdOrderByDayOfWeekAscStartTimeAsc(advertId, cabinetId).stream()
                .filter(s -> s.getDayOfWeek() == dayOfWeek)
                .filter(s -> excludeSlotId == null || !s.getId().equals(excludeSlotId))
                .toList();
        for (WbCampaignScheduleSlot existing : onDay) {
            if (WbCampaignSlotTimeUtils.overlaps(start, end, existing.getStartTime(), existing.getEndTime())) {
                throw new IllegalArgumentException(
                        "Слот пересекается с другим ("
                                + formatSlotRange(existing.getStartTime(), existing.getEndTime()) + ", "
                                + dayName(dayOfWeek) + ")");
            }
        }
    }

    private static String formatSlotRange(LocalTime start, LocalTime end) {
        return WbCampaignSlotTimeUtils.format(start) + "-" + WbCampaignSlotTimeUtils.formatEnd(start, end);
    }

    private static List<Short> resolveDays(Short singleDay, WbCampaignSlotRepeatMode mode, boolean repeat) {
        if (!repeat && singleDay != null) {
            return List.of(singleDay);
        }
        WbCampaignSlotRepeatMode effectiveMode = mode != null ? mode : WbCampaignSlotRepeatMode.DAILY;
        return switch (effectiveMode) {
            case DAILY -> List.of((short) 1, (short) 2, (short) 3, (short) 4, (short) 5, (short) 6, (short) 7);
            case WEEKENDS -> List.of((short) 6, (short) 7);
            case WEEKDAYS -> List.of((short) 1, (short) 2, (short) 3, (short) 4, (short) 5);
        };
    }

    private static String formatScheduleLabel(boolean repeat, Short dayOfWeek, WbCampaignSlotRepeatMode mode) {
        if (!repeat) {
            return dayOfWeek != null ? dayName(dayOfWeek) : "один день";
        }
        return repeatLabel(mode != null ? mode : WbCampaignSlotRepeatMode.DAILY);
    }

    private static String repeatLabel(WbCampaignSlotRepeatMode mode) {
        return switch (mode) {
            case DAILY -> "ежедневно";
            case WEEKENDS -> "только выходные";
            case WEEKDAYS -> "только будни";
        };
    }

    private static String dayName(short day) {
        return switch (day) {
            case 1 -> "понедельник";
            case 2 -> "вторник";
            case 3 -> "среда";
            case 4 -> "четверг";
            case 5 -> "пятница";
            case 6 -> "суббота";
            case 7 -> "воскресенье";
            default -> "день " + day;
        };
    }
}
