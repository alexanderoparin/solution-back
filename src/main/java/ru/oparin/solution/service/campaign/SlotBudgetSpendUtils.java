package ru.oparin.solution.service.campaign;

import ru.oparin.solution.model.WbCampaignManagementState;

/**
 * Расчёт расхода бюджета РК в рамках слота расписания.
 */
public final class SlotBudgetSpendUtils {

    private SlotBudgetSpendUtils() {
    }

    /**
     * Потрачено за слот: разница остатка бюджета WB на входе и сейчас + пополнения за слот.
     */
    public static int computeSpentRub(WbCampaignManagementState state, int currentBudgetTotal) {
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
    public static void reconcileBaselineIfBalanceGrew(WbCampaignManagementState state, int currentBudgetTotal) {
        if (state.getBudgetAtSlotStart() == null) {
            return;
        }
        int topUps = state.getSlotTopUpsRub();
        int expectedCeiling = state.getBudgetAtSlotStart() + topUps;
        if (currentBudgetTotal > expectedCeiling) {
            state.setBudgetAtSlotStart(currentBudgetTotal - topUps);
        }
    }

    public static boolean isSlotBudgetExhausted(WbCampaignManagementState state, Long slotId) {
        return slotId != null && slotId.equals(state.getSlotBudgetExhaustedSlotId());
    }

    public static void markSlotBudgetExhausted(WbCampaignManagementState state, Long slotId) {
        state.setSlotBudgetExhaustedSlotId(slotId);
    }

    public static void resetSlotSession(WbCampaignManagementState state) {
        state.setActiveSlotId(null);
        state.setBudgetAtSlotStart(null);
        state.setSlotBudgetExhaustedSlotId(null);
        state.setSlotTopUpsRub(0);
    }

    public static void beginSlotSession(WbCampaignManagementState state, Long slotId, int budgetAtStart) {
        state.setActiveSlotId(slotId);
        state.setSlotBudgetExhaustedSlotId(null);
        state.setSlotTopUpsRub(0);
        state.setBudgetAtSlotStart(budgetAtStart);
    }

    public static void addSlotTopUp(WbCampaignManagementState state, int amountRub) {
        state.setSlotTopUpsRub(state.getSlotTopUpsRub() + amountRub);
    }
}
