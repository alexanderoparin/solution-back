package ru.oparin.solution.service.campaign;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import ru.oparin.solution.model.Cabinet;
import ru.oparin.solution.model.WbCampaignManagementState;
import ru.oparin.solution.repository.WbCampaignManagementStateRepository;
import ru.oparin.solution.service.CabinetService;

import java.util.ArrayList;
import java.util.List;

/**
 * Пакетный HTTP-запрос бюджетов WB для кандидатов кабинета.
 * HTTP к WB выполняется вне транзакции, чтобы не держать соединение Hikari во время ожидания ответа.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CabinetBudgetBatchPollExecutor {

    private final CabinetService cabinetService;
    private final WbCampaignManagementStateRepository stateRepository;
    private final WbCampaignBudgetFetchService budgetFetchService;
    private final WbCabinetBudgetPollCoordinator budgetPollCoordinator;

    @Lazy
    @Autowired
    private CabinetBudgetBatchPollExecutor self;

    /**
     * Опрашивает бюджеты кандидатов кабинета и сохраняет кэш в состоянии РК.
     */
    public void pollCabinet(Long cabinetId, List<Long> advertIds) {
        if (advertIds == null || advertIds.isEmpty()) {
            return;
        }
        BatchPollContext ctx = self.loadContext(cabinetId, advertIds);
        if (ctx == null) {
            return;
        }
        try {
            budgetFetchService.fetchBudgetsForCampaigns(ctx.cabinet(), ctx.states());
            self.persistStates(ctx.states());
        } catch (Exception e) {
            log.warn("Не удалось пакетно опросить бюджет cabinetId={}: {}", cabinetId, e.getMessage());
            budgetPollCoordinator.revokeCabinet(cabinetId);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public BatchPollContext loadContext(Long cabinetId, List<Long> advertIds) {
        Cabinet cabinet = cabinetService.findById(cabinetId).orElse(null);
        if (cabinet == null || cabinet.getApiKey() == null || cabinet.getApiKey().isBlank()) {
            return null;
        }
        List<WbCampaignManagementState> states = new ArrayList<>();
        for (Long advertId : advertIds) {
            WbCampaignManagementState state = stateRepository.findById(advertId).orElse(null);
            if (state != null) {
                states.add(state);
            }
        }
        if (states.isEmpty()) {
            return null;
        }
        return new BatchPollContext(cabinet, states);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void persistStates(List<WbCampaignManagementState> states) {
        stateRepository.saveAll(states);
    }

    /**
     * Контекст кабинета после короткой read-only транзакции (далее сущности могут быть detached).
     */
    public record BatchPollContext(Cabinet cabinet, List<WbCampaignManagementState> states) {
    }
}
