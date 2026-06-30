package ru.oparin.solution.service.campaign;

import ru.oparin.solution.model.CampaignManagementState;

/**
 * Расчёт расхода бюджета РК в рамках слота расписания.
 */
public final class SlotBudgetSpendUtils {

    private SlotBudgetSpendUtils() {
    }

    /**
     * Потрачено за слот: разница остатка бюджета WB на входе и сейчас + пополнения за слот.
     */
    public static int computeSpentRub(CampaignManagementState state, int currentBudgetTotal) {
        if (state.getBudgetAtSlotStart() == null) {
            return 0;
        }
        reconcileBaselineIfBalanceGrew(state, currentBudgetTotal);
        int topUps = state.getSlotTopUpsRub();
        return Math.max(0, state.getBudgetAtSlotStart() - currentBudgetTotal + topUps);
    }

    /**
     * Баланс WB вырос без учтённого пополнения — поднимаем базу слота, чтобы лимит расхода не «обнулялся».
     */
    public static void reconcileBaselineIfBalanceGrew(CampaignManagementState state, int currentBudgetTotal) {
        if (state.getBudgetAtSlotStart() == null) {
            return;
        }
        int topUps = state.getSlotTopUpsRub();
        int expectedCeiling = state.getBudgetAtSlotStart() + topUps;
        if (currentBudgetTotal > expectedCeiling) {
            state.setBudgetAtSlotStart(currentBudgetTotal - topUps);
        }
    }

    public static boolean isSlotBudgetExhausted(CampaignManagementState state, Long slotId) {
        return slotId != null && slotId.equals(state.getSlotBudgetExhaustedSlotId());
    }

    public static void markSlotBudgetExhausted(CampaignManagementState state, Long slotId) {
        state.setSlotBudgetExhaustedSlotId(slotId);
    }

    public static void resetSlotSession(CampaignManagementState state) {
        state.setActiveSlotId(null);
        state.setBudgetAtSlotStart(null);
        state.setSlotBudgetExhaustedSlotId(null);
        state.setSlotTopUpsRub(0);
    }

    public static void beginSlotSession(CampaignManagementState state, Long slotId, int budgetAtStart) {
        state.setActiveSlotId(slotId);
        state.setSlotBudgetExhaustedSlotId(null);
        state.setSlotTopUpsRub(0);
        state.setBudgetAtSlotStart(budgetAtStart);
    }

    public static void addSlotTopUp(CampaignManagementState state, int amountRub) {
        state.setSlotTopUpsRub(state.getSlotTopUpsRub() + amountRub);
    }
}
