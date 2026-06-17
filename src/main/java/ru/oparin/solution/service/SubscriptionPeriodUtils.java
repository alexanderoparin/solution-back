package ru.oparin.solution.service;

import ru.oparin.solution.model.Plan;
import ru.oparin.solution.model.PlanPeriodType;

import java.time.LocalDateTime;

/**
 * Расчёт даты окончания подписки по плану.
 */
public final class SubscriptionPeriodUtils {

    private SubscriptionPeriodUtils() {
    }

    public static LocalDateTime addPlanPeriod(LocalDateTime base, Plan plan) {
        return addPlanPeriod(base, plan.getPeriodDays(), plan.getPeriodType());
    }

    /**
     * Продлевает период по снимку тарифа (без привязки к актуальной записи plans).
     */
    public static LocalDateTime addPlanPeriod(LocalDateTime base, int periodDays, PlanPeriodType periodType) {
        if (periodType == PlanPeriodType.CALENDAR_MONTH) {
            return base.plusMonths(1);
        }
        return base.plusDays(periodDays);
    }
}
