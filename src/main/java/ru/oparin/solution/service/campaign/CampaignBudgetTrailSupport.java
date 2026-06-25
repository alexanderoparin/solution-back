package ru.oparin.solution.service.campaign;

import java.time.LocalDateTime;

/**
 * Хвост опроса бюджета после паузы РК и горизонталь на графике до следующего START.
 */
public final class CampaignBudgetTrailSupport {

    /** Сколько минут после STOP опрашивать бюджет WB. */
    public static final int TRAIL_MINUTES_AFTER_PAUSE = 5;

    private CampaignBudgetTrailSupport() {
    }

    public static LocalDateTime trailEndAfter(LocalDateTime stopAt) {
        return stopAt.plusMinutes(TRAIL_MINUTES_AFTER_PAUSE);
    }
}
