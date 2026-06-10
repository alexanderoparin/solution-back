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
        int topUps = state.getSlotTopUpsRub();
        return state.getBudgetAtSlotStart() - currentBudgetTotal + topUps;
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
