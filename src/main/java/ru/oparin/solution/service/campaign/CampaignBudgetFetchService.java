package ru.oparin.solution.service.campaign;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.oparin.solution.dto.wb.PromotionBudgetResponse;
import ru.oparin.solution.model.Cabinet;
import ru.oparin.solution.model.CabinetTokenType;
import ru.oparin.solution.model.CampaignManagementState;
import ru.oparin.solution.model.WbApiEventType;
import ru.oparin.solution.service.wb.WbPromotionApiClient;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Objects;
import java.util.Optional;

/**
 * Получение бюджета РК из WB с учётом лимитов и кэша в состоянии управления.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class CampaignBudgetFetchService {

    private static final ZoneId ZONE = ZoneId.of("Europe/Moscow");

    private final WbPromotionApiClient promotionApiClient;
    private final CampaignBudgetTimelineService timelineService;
    private final CabinetBudgetPollCoordinator budgetPollCoordinator;
    private final CampaignBudgetPollEligibility pollEligibility;

    /**
     * Возвращает бюджет кампании: из кэша состояния, если лимит не позволяет запрос, иначе — свежий ответ WB.
     * В тике планировщика HTTP к WB разрешён только лидеру очереди кабинета ({@link CabinetBudgetPollCoordinator}).
     */
    public Optional<Integer> fetchBudgetTotal(Cabinet cabinet, Long advertId, CampaignManagementState state) {
        return fetchBudgetTotalInternal(cabinet, advertId, state, false);
    }

    /**
     * Бюджет для решений (автопополнение): только свежий ответ WB или актуальный кэш, без устаревшего fallback.
     */
    public Optional<Integer> fetchBudgetForDecision(Cabinet cabinet, Long advertId, CampaignManagementState state) {
        return fetchBudgetTotalInternal(cabinet, advertId, state, true);
    }

    /**
     * Бюджет при входе в слот: в первый раз за календарный день (МСК) — только ответ WB, без вчерашнего кэша.
     */
    public Optional<Integer> fetchBudgetForSlotEnter(Cabinet cabinet, Long advertId, CampaignManagementState state) {
        boolean requireFreshFromWb = isBudgetCacheFromPreviousDay(state);
        return fetchBudgetTotalInternal(cabinet, advertId, state, requireFreshFromWb);
    }

    private Optional<Integer> fetchBudgetTotalInternal(
            Cabinet cabinet,
            Long advertId,
            CampaignManagementState state,
            boolean rejectStaleCacheOnly
    ) {
        if (cabinet.getApiKey() == null || cabinet.getApiKey().isBlank()) {
            return Optional.empty();
        }
        CabinetTokenType tokenType = cabinet.getTokenType() != null ? cabinet.getTokenType() : CabinetTokenType.BASIC;
        if (state != null && pollEligibility.isSlotBudgetCapPaused(
                state, advertId, cabinet.getId(), ZonedDateTime.now(ZONE))) {
            return cachedBudget(state);
        }
        Optional<Integer> freshCached = freshCachedBudget(state, tokenType);
        if (freshCached.isPresent()) {
            return freshCached;
        }
        Optional<Integer> tickPollCache = cachedBudgetFromThisSchedulerTick(
                cabinet.getId(), advertId, state, rejectStaleCacheOnly);
        if (tickPollCache.isPresent()) {
            return tickPollCache;
        }
        if (!budgetPollCoordinator.mayCallWbApi(cabinet.getId(), advertId)) {
            return rejectStaleCacheOnly
                    ? Optional.empty()
                    : cachedBudget(state);
        }
        try {
            PromotionBudgetResponse budget = promotionApiClient.getCampaignBudget(cabinet.getApiKey(), advertId);
            if (budget == null || budget.getTotal() == null) {
                if (rejectStaleCacheOnly) {
                    return Optional.empty();
                }
                return state != null && state.getLastBudgetTotal() != null
                        ? Optional.of(state.getLastBudgetTotal())
                        : Optional.empty();
            }
            if (state != null) {
                state.setLastBudgetTotal(budget.getTotal());
                state.setLastBudgetCheckedAt(LocalDateTime.now(ZONE));
            }
            timelineService.recordSnapshot(advertId, cabinet.getId(), budget.getTotal());
            budgetPollCoordinator.markBudgetPolledThisTick(cabinet.getId(), advertId);
            return Optional.of(budget.getTotal());
        } catch (Exception e) {
            log.debug("Не удалось получить бюджет РК advertId={}: {}", advertId, e.getMessage());
            if (rejectStaleCacheOnly) {
                LocalDateTime checkedBefore = state != null ? state.getLastBudgetCheckedAt() : null;
                Optional<Integer> cached = cachedBudget(state);
                if (cached.isEmpty() || state == null) {
                    return Optional.empty();
                }
                if (Objects.equals(checkedBefore, state.getLastBudgetCheckedAt())
                        && isFresh(state.getLastBudgetCheckedAt(), tokenType)) {
                    return cached;
                }
                return Optional.empty();
            }
            return cachedBudget(state);
        }
    }

    private Optional<Integer> cachedBudget(CampaignManagementState state) {
        if (state != null && state.getLastBudgetTotal() != null) {
            return Optional.of(state.getLastBudgetTotal());
        }
        return Optional.empty();
    }

    /**
     * Кэш после единственного HTTP-опроса в текущем тике планировщика (пауза endpoint ~1 с не мешает).
     */
    private Optional<Integer> cachedBudgetFromThisSchedulerTick(
            Long cabinetId,
            Long advertId,
            CampaignManagementState state,
            boolean rejectStaleCacheOnly
    ) {
        if (!budgetPollCoordinator.wasBudgetPolledThisTick(cabinetId, advertId)) {
            return Optional.empty();
        }
        if (rejectStaleCacheOnly) {
            return todayCachedBudget(state);
        }
        return cachedBudget(state);
    }

    private Optional<Integer> todayCachedBudget(CampaignManagementState state) {
        if (state == null
                || state.getLastBudgetTotal() == null
                || isBudgetCacheFromPreviousDay(state)) {
            return Optional.empty();
        }
        return Optional.of(state.getLastBudgetTotal());
    }

    /**
     * Актуальный кэш за сегодня (МСК), ещё в пределах паузы endpoint — без повторного HTTP.
     */
    private Optional<Integer> freshCachedBudget(CampaignManagementState state, CabinetTokenType tokenType) {
        if (state == null
                || state.getLastBudgetTotal() == null
                || state.getLastBudgetCheckedAt() == null
                || isBudgetCacheFromPreviousDay(state)
                || !isFresh(state.getLastBudgetCheckedAt(), tokenType)) {
            return Optional.empty();
        }
        return Optional.of(state.getLastBudgetTotal());
    }

    /**
     * Сохраняет известный остаток бюджета без запроса к WB (например, после deposit с returnBudget).
     */
    public void storeBudgetTotal(
            CampaignManagementState state,
            Long advertId,
            Long cabinetId,
            int budgetTotal
    ) {
        if (state != null) {
            state.setLastBudgetTotal(budgetTotal);
            state.setLastBudgetCheckedAt(LocalDateTime.now(ZONE));
        }
        timelineService.recordSnapshot(advertId, cabinetId, budgetTotal);
    }

    /**
     * Остаток после пополнения: из ответа WB или оценка «было + сумма».
     */
    public int resolveBudgetAfterTopUp(
            int budgetBeforeTopUp,
            int topUpAmount,
            PromotionBudgetResponse depositResponse
    ) {
        int estimated = budgetBeforeTopUp + topUpAmount;
        if (depositResponse != null && depositResponse.getTotal() != null) {
            return Math.max(depositResponse.getTotal(), estimated);
        }
        return estimated;
    }

    private boolean isFresh(LocalDateTime checkedAt, CabinetTokenType tokenType) {
        long delayMs = WbApiEventType.PROMOTION_BUDGET_GET.getRequestDelayMs(tokenType);
        return checkedAt.plusNanos(delayMs * 1_000_000L).isAfter(LocalDateTime.now(ZONE));
    }

    /**
     * {@code true}, если последний известный остаток относится к предыдущему календарному дню (МСК) или отсутствует.
     */
    private boolean isBudgetCacheFromPreviousDay(CampaignManagementState state) {
        if (state == null || state.getLastBudgetCheckedAt() == null) {
            return true;
        }
        LocalDate cacheDay = state.getLastBudgetCheckedAt().atZone(ZONE).toLocalDate();
        return !cacheDay.equals(LocalDate.now(ZONE));
    }
}
