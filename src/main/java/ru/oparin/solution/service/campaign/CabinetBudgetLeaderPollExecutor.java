package ru.oparin.solution.service.campaign;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import ru.oparin.solution.model.Cabinet;
import ru.oparin.solution.model.CampaignManagementState;
import ru.oparin.solution.repository.CampaignManagementStateRepository;
import ru.oparin.solution.service.CabinetService;

/**
 * Один HTTP-запрос бюджета WB для round-robin лидера кабинета.
 * HTTP к WB выполняется вне транзакции, чтобы не держать соединение Hikari во время ожидания ответа.
 */
@Service
@RequiredArgsConstructor
public class CabinetBudgetLeaderPollExecutor {

    private final CabinetService cabinetService;
    private final CampaignManagementStateRepository stateRepository;
    private final CampaignBudgetFetchService budgetFetchService;

    @Lazy
    @Autowired
    private CabinetBudgetLeaderPollExecutor self;

    /**
     * Опрашивает бюджет лидера очереди и сохраняет кэш в состоянии РК.
     */
    public void pollLeaderInNewTransaction(Long cabinetId, Long advertId) {
        LeaderPollContext ctx = self.loadLeaderContext(cabinetId, advertId);
        if (ctx == null) {
            return;
        }
        // HTTP + мутации state в памяти; timeline.recordSnapshot — в своей короткой TX.
        budgetFetchService.fetchBudgetTotal(ctx.cabinet(), advertId, ctx.state());
        self.persistLeaderState(ctx.state());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public LeaderPollContext loadLeaderContext(Long cabinetId, Long advertId) {
        Cabinet cabinet = cabinetService.findById(cabinetId).orElse(null);
        CampaignManagementState state = stateRepository.findById(advertId).orElse(null);
        if (cabinet == null || state == null) {
            return null;
        }
        return new LeaderPollContext(cabinet, state);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void persistLeaderState(CampaignManagementState state) {
        stateRepository.save(state);
    }

    /**
     * Контекст лидера после короткой read-only транзакции (далее сущность может быть detached).
     */
    public record LeaderPollContext(Cabinet cabinet, CampaignManagementState state) {
    }
}
