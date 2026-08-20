package ru.oparin.solution.service.campaign;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.oparin.solution.model.Cabinet;
import ru.oparin.solution.model.WbCampaignManagementState;

import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * Хвост опроса бюджета после паузы рекламной кампании.
 */
@Service
@RequiredArgsConstructor
public class WbCampaignBudgetTrailService {

    private static final ZoneId ZONE = ZoneId.of("Europe/Moscow");

    private final WbCampaignBudgetFetchService budgetFetchService;

    /**
     * Запускает trail: ещё {@link WbCampaignBudgetTrailSupport#TRAIL_MINUTES_AFTER_PAUSE} минут опроса бюджета.
     */
    public void beginTrail(WbCampaignManagementState state) {
        state.setBudgetTrailUntil(LocalDateTime.now(ZONE).plusMinutes(WbCampaignBudgetTrailSupport.TRAIL_MINUTES_AFTER_PAUSE));
    }

    public void clearTrail(WbCampaignManagementState state) {
        state.setBudgetTrailUntil(null);
    }

    /**
     * Опрашивает бюджет, пока trail активен; по истечении сбрасывает {@code budgetTrailUntil}.
     */
    public void pollDuringTrailIfNeeded(Cabinet cabinet, WbCampaignManagementState state) {
        LocalDateTime trailUntil = state.getBudgetTrailUntil();
        if (trailUntil == null) {
            return;
        }
        LocalDateTime now = LocalDateTime.now(ZONE);
        if (now.isAfter(trailUntil)) {
            state.setBudgetTrailUntil(null);
            return;
        }
        budgetFetchService.fetchBudgetTotal(cabinet, state.getCampaignId(), state);
    }
}
