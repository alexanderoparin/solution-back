package ru.oparin.solution.service.campaign;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.oparin.solution.dto.analytics.CampaignControlEnqueueResponse;
import ru.oparin.solution.dto.analytics.manage.*;
import ru.oparin.solution.model.*;
import ru.oparin.solution.repository.CampaignAutoBudgetSettingsRepository;
import ru.oparin.solution.repository.CampaignManagementStateRepository;
import ru.oparin.solution.repository.CampaignScheduleSlotRepository;
import ru.oparin.solution.repository.PromotionCampaignRepository;
import ru.oparin.solution.service.AnalyticsService;
import ru.oparin.solution.service.CabinetService;
import ru.oparin.solution.service.PromotionCampaignControlService;
import ru.oparin.solution.service.PromotionCampaignControlWriteService;
import ru.oparin.solution.service.wb.WbPromotionApiClient;

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
public class CampaignManageService {

    private static final ZoneId SCHEDULE_ZONE = ZoneId.of("Europe/Moscow");
    private final AnalyticsService analyticsService;
    private final PromotionCampaignRepository campaignRepository;
    private final CampaignAutoBudgetSettingsRepository autoBudgetRepository;
    private final CampaignScheduleSlotRepository slotRepository;
    private final CampaignManagementStateRepository stateRepository;
    private final CampaignChangeLogService changeLogService;
    private final PromotionCampaignControlService controlService;
    private final PromotionCampaignControlWriteService controlWriteService;
    private final CabinetService cabinetService;
    private final WbPromotionApiClient promotionApiClient;
    private final CabinetPromotionBalanceCacheService balanceCacheService;
    private final CampaignBudgetTimelineService timelineService;
    private final CampaignBudgetFetchService budgetFetchService;
    private final CampaignBudgetChartService budgetChartService;
    private final CampaignManageAccessService campaignManageAccessService;
    private final BidderStatusResolver bidderStatusResolver;
    private final CampaignBudgetTrailService budgetTrailService;

    @Transactional(readOnly = true)
    public CampaignManageResponseDto getManage(Long advertId, Long cabinetId, User seller) {
        var detail = analyticsService.getCampaignDetail(advertId, cabinetId, seller != null ? seller.getId() : null);
        if (detail == null) {
            return null;
        }
        CampaignManagementState state = stateOrDefaults(advertId, cabinetId);
        PromotionCampaign campaign = campaignRepository.findByAdvertIdAndCabinet_Id(advertId, cabinetId).orElse(null);
        List<CampaignScheduleSlot> slots = slotRepository
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
        CampaignAutoBudgetSettings settings = getOrCreateAutoBudget(advertId, cabinetId);
        settings.setEnabled(request.isEnabled());
        settings.setTopUpAmount(request.getTopUpAmount());
        settings.setSourceType(request.getSourceType());
        settings.setThresholdRub(request.getThresholdRub());
        settings.setMaxTopUpsPerDay(request.getMaxTopUpsPerDay());
        settings.setLocked(true);
        autoBudgetRepository.save(settings);
        changeLogService.log(advertId, cabinetId, user, "Сохранены настройки автопополнения бюджета");
        return mapAutoBudget(settings);
    }

    @Transactional
    public CampaignAutoBudgetDto unlockAutoBudget(Long advertId, Long cabinetId, User user) {
        ensureCampaign(advertId, cabinetId);
        CampaignAutoBudgetSettings settings = getOrCreateAutoBudget(advertId, cabinetId);
        settings.setLocked(false);
        autoBudgetRepository.save(settings);
        changeLogService.log(advertId, cabinetId, user, "Редактирование настроек автопополнения бюджета");
        return mapAutoBudget(settings);
    }

    @Transactional
    public List<CampaignScheduleSlotDto> createSlots(
            Long advertId, Long cabinetId, User user, CampaignScheduleSlotRequestDto request
    ) {
        ensureCampaign(advertId, cabinetId);
        LocalTime start = CampaignSlotTimeUtils.parseStartHHmm(request.getStartTime());
        LocalTime end = CampaignSlotTimeUtils.parseEndHHmm(request.getEndTime());
        if (!CampaignSlotTimeUtils.isEndAfterStart(start, end)) {
            throw new IllegalArgumentException("Время окончания должно быть позже начала");
        }
        if (request.getBudgetRub() == null || request.getBudgetRub() <= 0) {
            throw new IllegalArgumentException("Укажите бюджет слота");
        }
        UUID groupId = request.isRepeat() ? UUID.randomUUID() : null;
        CampaignSlotRepeatMode mode = request.isRepeat()
                ? parseRepeatMode(request.getRepeatMode())
                : CampaignSlotRepeatMode.DAILY;
        List<Short> days = resolveDays(request.getDayOfWeek(), mode, request.isRepeat());
        for (Short day : days) {
            ensureNoSlotOverlap(advertId, cabinetId, day, start, end, null);
        }
        List<CampaignScheduleSlot> created = new ArrayList<>();
        for (Short day : days) {
            CampaignScheduleSlot slot = CampaignScheduleSlot.builder()
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
        String repeatLabel = repeatLabel(mode);
        changeLogService.log(advertId, cabinetId, user,
                "Добавлен слот «" + formatSlotRange(start, end) + ", " + repeatLabel + "»");
        applySlotEditPolicy(advertId, cabinetId, stateRepository.findById(advertId).orElse(null));
        return created.stream().map(this::mapSlot).toList();
    }

    @Transactional
    public CampaignScheduleSlotDto updateSlot(
            Long advertId, Long cabinetId, Long slotId, User user, CampaignScheduleSlotUpdateDto request
    ) {
        ensureCampaign(advertId, cabinetId);
        CampaignScheduleSlot slot = slotRepository.findById(slotId)
                .orElseThrow(() -> new IllegalArgumentException("Слот не найден"));
        if (!slot.getCampaignId().equals(advertId) || !slot.getCabinetId().equals(cabinetId)) {
            throw new IllegalArgumentException("Слот не принадлежит этой кампании");
        }
        String oldTime = formatSlotRange(slot.getStartTime(), slot.getEndTime());
        Integer oldBudget = slot.getBudgetRub();
        LocalTime oldEnd = slot.getEndTime();
        if (request.getStartTime() != null) {
            slot.setStartTime(CampaignSlotTimeUtils.parseStartHHmm(request.getStartTime()));
        }
        if (request.getEndTime() != null) {
            slot.setEndTime(CampaignSlotTimeUtils.parseEndHHmm(request.getEndTime()));
        }
        if (!CampaignSlotTimeUtils.isEndAfterStart(slot.getStartTime(), slot.getEndTime())) {
            throw new IllegalArgumentException("Время окончания должно быть позже начала");
        }
        if (request.getBudgetRub() != null) {
            slot.setBudgetRub(request.getBudgetRub());
        }
        ensureNoSlotOverlap(advertId, cabinetId, slot.getDayOfWeek(), slot.getStartTime(), slot.getEndTime(), slot.getId());
        slotRepository.save(slot);
        CampaignManagementState state = stateRepository.findById(advertId).orElse(null);
        if (request.getBudgetRub() != null && !Objects.equals(oldBudget, request.getBudgetRub())) {
            changeLogService.log(advertId, cabinetId, user,
                    "Изменен бюджет «было " + oldBudget + ", стало " + request.getBudgetRub() + "»");
        }
        if (request.getStartTime() != null || request.getEndTime() != null) {
            String newTime = formatSlotRange(slot.getStartTime(), slot.getEndTime());
            if (!oldTime.equals(newTime)) {
                changeLogService.log(advertId, cabinetId, user,
                        "Изменено время «было " + oldTime + ", стало " + newTime + "»");
            }
        }
        applySlotEditPolicyAfterUpdate(advertId, cabinetId, state, slot, oldEnd, oldBudget, request);
        return mapSlot(slot);
    }

    @Transactional
    public void deleteSlot(Long advertId, Long cabinetId, Long slotId, User user) {
        ensureCampaign(advertId, cabinetId);
        CampaignScheduleSlot slot = slotRepository.findById(slotId)
                .orElseThrow(() -> new IllegalArgumentException("Слот не найден"));
        String msg = "Удален слот «" + formatSlotRange(slot.getStartTime(), slot.getEndTime())
                + ", " + dayName(slot.getDayOfWeek()) + "»";
        slotRepository.delete(slot);
        changeLogService.log(advertId, cabinetId, user, msg);
        applySlotEditPolicy(advertId, cabinetId, stateRepository.findById(advertId).orElse(null));
    }

    @Transactional
    public CampaignControlEnqueueResponse manualStart(Long advertId, Long cabinetId, User user) {
        Cabinet cabinet = cabinetService.findById(cabinetId)
                .orElseThrow(() -> new IllegalArgumentException("Кабинет не найден"));
        CampaignManagementState state = getOrCreateState(advertId, cabinetId);
        state.setManualStopped(false);
        stateRepository.save(state);

        ZonedDateTime now = ZonedDateTime.now(SCHEDULE_ZONE);
        boolean inActiveSlot = findActiveSlotNow(advertId, cabinetId, now).isPresent();
        if (inActiveSlot) {
            budgetTrailService.clearTrail(state);
            changeLogService.log(advertId, cabinetId, user, "Расписание включено");
            timelineService.recordStart(advertId, cabinetId);
            return controlService.enqueueStart(cabinet, advertId);
        }

        changeLogService.log(advertId, cabinetId, user, "Расписание включено");
        return new CampaignControlEnqueueResponse(false, null, "Расписание включено");
    }

    @Transactional
    public CampaignControlEnqueueResponse manualPause(Long advertId, Long cabinetId, User user) {
        Cabinet cabinet = cabinetService.findById(cabinetId)
                .orElseThrow(() -> new IllegalArgumentException("Кабинет не найден"));
        CampaignManagementState state = getOrCreateState(advertId, cabinetId);
        state.setManualStopped(true);
        state.setActiveSlotId(null);
        state.setBudgetAtSlotStart(null);
        stateRepository.save(state);
        changeLogService.log(advertId, cabinetId, user, "Расписание выключено");

        PromotionCampaign campaign = campaignRepository.findByAdvertIdAndCabinet_Id(advertId, cabinetId).orElse(null);
        if (campaign != null && campaign.getStatus() == CampaignStatus.ACTIVE) {
            timelineService.recordStop(advertId, cabinetId);
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
            CampaignManagementState state,
            Cabinet cabinet,
            User seller
    ) {
        if (state == null || state.isManualStopped() || !state.isScheduleEnabled()) {
            return;
        }
        Long advertId = state.getCampaignId();
        Long cabinetId = cabinet.getId();
        PromotionCampaign campaign = campaignRepository.findByAdvertIdAndCabinet_Id(advertId, cabinetId).orElse(null);
        boolean campaignWasActive = campaign != null && campaign.getStatus() == CampaignStatus.ACTIVE;

        if (campaignWasActive) {
            if (!controlWriteService.getCapabilities(cabinet).canControl()) {
                return;
            }
            try {
                controlService.enqueuePause(cabinet, advertId);
                timelineService.recordStop(advertId, cabinetId);
                budgetTrailService.beginTrail(state);
            } catch (Exception ignored) {
                return;
            }
        }

        String message = campaignManageAccessService.scheduleStopMessageForSeller(seller);
        if (campaignWasActive) {
            message = message + " Активная РК остановлена.";
        }
        state.setManualStopped(true);
        SlotBudgetSpendUtils.resetSlotSession(state);
        changeLogService.log(advertId, cabinetId, null, message);
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
    public Page<CampaignChangeLogEntryDto> changeLogPage(Long advertId, Long cabinetId, int page, int size) {
        return changeLogService.page(advertId, cabinetId, page, size);
    }

    public Optional<CampaignScheduleSlot> findActiveSlotNow(Long advertId, Long cabinetId, ZonedDateTime now) {
        List<CampaignScheduleSlot> slots = slotRepository
                .findByCampaignIdAndCabinetIdOrderByDayOfWeekAscStartTimeAsc(advertId, cabinetId);
        return bidderStatusResolver.findActiveSlotNow(slots, now);
    }

    private void applySlotEditPolicy(Long advertId, Long cabinetId, CampaignManagementState state) {
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
            CampaignManagementState state,
            CampaignScheduleSlot slot,
            LocalTime oldEnd,
            Integer oldBudget,
            CampaignScheduleSlotUpdateDto request
    ) {
        if (state == null || state.isManualStopped()) {
            return;
        }
        ZonedDateTime now = ZonedDateTime.now(SCHEDULE_ZONE);
        Optional<CampaignScheduleSlot> active = findActiveSlotNow(advertId, cabinetId, now);
        if (active.isEmpty() || !active.get().getId().equals(slot.getId())) {
            applySlotEditPolicy(advertId, cabinetId, state);
            return;
        }
        LocalTime nowTime = CampaignSlotTimeUtils.snap(now.toLocalTime());
        if (request.getEndTime() != null && !oldEnd.equals(slot.getEndTime())) {
            if (CampaignSlotTimeUtils.toMinutes(nowTime) >= CampaignSlotTimeUtils.endMinutes(
                    slot.getStartTime(), slot.getEndTime())) {
                pauseIfActive(advertId, cabinetId, state, "РК остановлена: время слота сокращено до текущего момента");
            }
        }
        if (request.getBudgetRub() != null && oldBudget != null && request.getBudgetRub() < oldBudget) {
            checkBudgetDecreasePause(advertId, cabinetId, state, slot.getBudgetRub());
        }
    }

    private void checkBudgetDecreasePause(Long advertId, Long cabinetId, CampaignManagementState state, int newBudgetRub) {
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

    private void pauseIfActive(Long advertId, Long cabinetId, CampaignManagementState state, String logMessage) {
        PromotionCampaign campaign = campaignRepository.findByAdvertIdAndCabinet_Id(advertId, cabinetId).orElse(null);
        if (campaign != null && campaign.getStatus() == CampaignStatus.ACTIVE) {
            try {
                Cabinet cabinet = cabinetService.findById(cabinetId).orElse(null);
                if (cabinet != null) {
                    controlService.enqueuePause(cabinet, advertId);
                    changeLogService.log(advertId, cabinetId, null, logMessage);
                    timelineService.recordStop(advertId, cabinetId);
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

    private CampaignManagementState stateOrDefaults(Long advertId, Long cabinetId) {
        return stateRepository.findById(advertId)
                .orElseGet(() -> CampaignManagementState.builder()
                        .campaignId(advertId)
                        .cabinetId(cabinetId)
                        .manualStopped(true)
                        .scheduleEnabled(true)
                        .topUpsTodayCount(0)
                        .build());
    }

    private CampaignAutoBudgetSettings autoBudgetOrDefaults(Long advertId, Long cabinetId) {
        return autoBudgetRepository.findById(advertId)
                .orElseGet(() -> CampaignAutoBudgetSettings.builder()
                        .campaignId(advertId)
                        .cabinetId(cabinetId)
                        .enabled(false)
                        .locked(false)
                        .build());
    }

    private CampaignManagementState getOrCreateState(Long advertId, Long cabinetId) {
        ensureCampaign(advertId, cabinetId);
        return stateRepository.findById(advertId)
                .orElseGet(() -> stateRepository.save(CampaignManagementState.builder()
                        .campaignId(advertId)
                        .cabinetId(cabinetId)
                        .manualStopped(true)
                        .scheduleEnabled(true)
                        .topUpsTodayCount(0)
                        .build()));
    }

    private CampaignAutoBudgetSettings getOrCreateAutoBudget(Long advertId, Long cabinetId) {
        ensureCampaign(advertId, cabinetId);
        return autoBudgetRepository.findById(advertId)
                .orElseGet(() -> autoBudgetRepository.save(CampaignAutoBudgetSettings.builder()
                        .campaignId(advertId)
                        .cabinetId(cabinetId)
                        .enabled(false)
                        .locked(false)
                        .build()));
    }

    private void ensureCampaign(Long advertId, Long cabinetId) {
        if (!campaignRepository.findByAdvertIdAndCabinet_Id(advertId, cabinetId).isPresent()) {
            throw new IllegalArgumentException("Кампания не найдена в этом кабинете");
        }
    }

    private List<CampaignScheduleSlotDto> loadSlots(Long advertId, Long cabinetId) {
        return slotRepository.findByCampaignIdAndCabinetIdOrderByDayOfWeekAscStartTimeAsc(advertId, cabinetId).stream()
                .map(this::mapSlot)
                .toList();
    }

    private CampaignAutoBudgetDto mapAutoBudget(CampaignAutoBudgetSettings s) {
        return CampaignAutoBudgetDto.builder()
                .enabled(s.isEnabled())
                .topUpAmount(s.getTopUpAmount())
                .sourceType(s.getSourceType())
                .thresholdRub(s.getThresholdRub())
                .maxTopUpsPerDay(s.getMaxTopUpsPerDay())
                .locked(s.isLocked())
                .build();
    }

    private CampaignScheduleSlotDto mapSlot(CampaignScheduleSlot s) {
        return CampaignScheduleSlotDto.builder()
                .id(s.getId())
                .dayOfWeek(s.getDayOfWeek())
                .startTime(CampaignSlotTimeUtils.format(s.getStartTime()))
                .endTime(CampaignSlotTimeUtils.formatEnd(s.getStartTime(), s.getEndTime()))
                .budgetRub(s.getBudgetRub())
                .repeatGroupId(s.getRepeatGroupId())
                .repeatMode(s.getRepeatMode() != null ? s.getRepeatMode().name() : null)
                .build();
    }

    private static CampaignSlotRepeatMode parseRepeatMode(String mode) {
        if (mode == null || mode.isBlank()) {
            return CampaignSlotRepeatMode.DAILY;
        }
        return CampaignSlotRepeatMode.valueOf(mode.trim().toUpperCase());
    }

    private void ensureNoSlotOverlap(
            Long advertId,
            Long cabinetId,
            short dayOfWeek,
            LocalTime start,
            LocalTime end,
            Long excludeSlotId
    ) {
        List<CampaignScheduleSlot> onDay = slotRepository
                .findByCampaignIdAndCabinetIdOrderByDayOfWeekAscStartTimeAsc(advertId, cabinetId).stream()
                .filter(s -> s.getDayOfWeek() == dayOfWeek)
                .filter(s -> excludeSlotId == null || !s.getId().equals(excludeSlotId))
                .toList();
        for (CampaignScheduleSlot existing : onDay) {
            if (CampaignSlotTimeUtils.overlaps(start, end, existing.getStartTime(), existing.getEndTime())) {
                throw new IllegalArgumentException(
                        "Слот пересекается с другим ("
                                + formatSlotRange(existing.getStartTime(), existing.getEndTime()) + ", "
                                + dayName(dayOfWeek) + ")");
            }
        }
    }

    private static String formatSlotRange(LocalTime start, LocalTime end) {
        return CampaignSlotTimeUtils.format(start) + "-" + CampaignSlotTimeUtils.formatEnd(start, end);
    }

    private static List<Short> resolveDays(Short singleDay, CampaignSlotRepeatMode mode, boolean repeat) {
        if (!repeat && singleDay != null) {
            return List.of(singleDay);
        }
        return switch (mode) {
            case DAILY -> List.of((short) 1, (short) 2, (short) 3, (short) 4, (short) 5, (short) 6, (short) 7);
            case WEEKENDS -> List.of((short) 6, (short) 7);
            case WEEKDAYS -> List.of((short) 1, (short) 2, (short) 3, (short) 4, (short) 5);
        };
    }

    private static String repeatLabel(CampaignSlotRepeatMode mode) {
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
