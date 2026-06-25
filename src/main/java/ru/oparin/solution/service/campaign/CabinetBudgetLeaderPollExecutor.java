package ru.oparin.solution.service.campaign;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import ru.oparin.solution.model.Cabinet;
import ru.oparin.solution.model.CampaignManagementState;
import ru.oparin.solution.repository.CampaignManagementStateRepository;
import ru.oparin.solution.service.CabinetService;

/**
 * Один HTTP-запрос бюджета WB для round-robin лидера кабинета в отдельной транзакции.
 */
@Service
@RequiredArgsConstructor
public class CabinetBudgetLeaderPollExecutor {

    private final CabinetService cabinetService;
    private final CampaignManagementStateRepository stateRepository;
    private final CampaignBudgetFetchService budgetFetchService;

    /**
     * Опрашивает бюджет лидера очереди и сохраняет кэш в состоянии РК.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void pollLeaderInNewTransaction(Long cabinetId, Long advertId) {
        Cabinet cabinet = cabinetService.findById(cabinetId).orElse(null);
        CampaignManagementState state = stateRepository.findById(advertId).orElse(null);
        if (cabinet == null || state == null) {
            return;
        }
        budgetFetchService.fetchBudgetTotal(cabinet, advertId, state);
        stateRepository.save(state);
    }
}
