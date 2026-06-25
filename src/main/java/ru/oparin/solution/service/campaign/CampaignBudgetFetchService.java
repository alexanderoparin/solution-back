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

import java.time.LocalDateTime;
import java.time.ZoneId;
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

    /**
     * Возвращает бюджет кампании: из кэша состояния, если лимит не позволяет запрос, иначе — свежий ответ WB.
     */
    public Optional<Integer> fetchBudgetTotal(Cabinet cabinet, Long advertId, CampaignManagementState state) {
        if (cabinet.getApiKey() == null || cabinet.getApiKey().isBlank()) {
            return Optional.empty();
        }
        CabinetTokenType tokenType = cabinet.getTokenType() != null ? cabinet.getTokenType() : CabinetTokenType.BASIC;
        if (state != null && state.getLastBudgetTotal() != null && state.getLastBudgetCheckedAt() != null
                && isFresh(state.getLastBudgetCheckedAt(), tokenType)) {
            return Optional.of(state.getLastBudgetTotal());
        }
        try {
            PromotionBudgetResponse budget = promotionApiClient.getCampaignBudget(cabinet.getApiKey(), advertId);
            if (budget == null || budget.getTotal() == null) {
                return state != null && state.getLastBudgetTotal() != null
                        ? Optional.of(state.getLastBudgetTotal())
                        : Optional.empty();
            }
            if (state != null) {
                state.setLastBudgetTotal(budget.getTotal());
                state.setLastBudgetCheckedAt(LocalDateTime.now(ZONE));
            }
            timelineService.recordSnapshot(advertId, cabinet.getId(), budget.getTotal());
            return Optional.of(budget.getTotal());
        } catch (Exception e) {
            log.debug("Не удалось получить бюджет РК advertId={}: {}", advertId, e.getMessage());
            if (state != null && state.getLastBudgetTotal() != null) {
                return Optional.of(state.getLastBudgetTotal());
            }
            return Optional.empty();
        }
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
}
