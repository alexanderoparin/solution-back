package ru.oparin.solution.service.campaign;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.oparin.solution.model.Cabinet;
import ru.oparin.solution.model.CampaignManagementState;

import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * Хвост опроса бюджета после паузы рекламной кампании.
 */
@Service
@RequiredArgsConstructor
public class CampaignBudgetTrailService {

    private static final ZoneId ZONE = ZoneId.of("Europe/Moscow");

    private final CampaignBudgetFetchService budgetFetchService;

    /**
     * Запускает trail: ещё {@link CampaignBudgetTrailSupport#TRAIL_MINUTES_AFTER_PAUSE} минут опроса бюджета.
     */
    public void beginTrail(CampaignManagementState state) {
        state.setBudgetTrailUntil(LocalDateTime.now(ZONE).plusMinutes(CampaignBudgetTrailSupport.TRAIL_MINUTES_AFTER_PAUSE));
    }

    public void clearTrail(CampaignManagementState state) {
        state.setBudgetTrailUntil(null);
    }

    /**
     * Опрашивает бюджет, пока trail активен; по истечении сбрасывает {@code budgetTrailUntil}.
     */
    public void pollDuringTrailIfNeeded(Cabinet cabinet, CampaignManagementState state) {
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
