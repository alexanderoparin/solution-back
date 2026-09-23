package ru.oparin.solution.service.campaign;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ru.oparin.solution.model.WbCampaignManagementState;
import ru.oparin.solution.repository.WbCampaignManagementStateRepository;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

/**
 * Планировщик: расписание слотов, лимит бюджета слота, автопополнение, сверка статуса с WB.
 * Каждая кампания обрабатывается в отдельной транзакции ({@link WbCampaignScheduleProcessor}).
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class WbCampaignScheduleOrchestrator {

    private static final ZoneId ZONE = ZoneId.of("Europe/Moscow");

    private final WbCampaignManagementStateRepository stateRepository;
    private final WbCampaignScheduleProcessor scheduleProcessor;
    private final WbCampaignSchedulePollPlanner pollPlanner;
    private final WbCabinetBudgetPollCoordinator budgetPollCoordinator;
    private final CabinetBudgetBatchPollExecutor batchPollExecutor;
    private final CabinetStatusReconcileExecutor statusReconcileExecutor;

    @Scheduled(cron = "0 * * * * *")
    @SchedulerLock(name = "campaignScheduleOrchestrator", lockAtLeastFor = "30s", lockAtMostFor = "55s")
    public void tick() {
        List<WbCampaignManagementState> states = stateRepository.findAll();
        ZonedDateTime now = ZonedDateTime.now(ZONE);
        Map<Long, List<Long>> pollCandidates = pollPlanner.collectBudgetPollCandidates(states, now);
        Map<Long, List<Long>> statusCandidates = pollPlanner.collectStatusReconcileCandidates(states, now);
        budgetPollCoordinator.beginSchedulerTick(pollCandidates);
        try {
            for (Map.Entry<Long, List<Long>> entry : pollCandidates.entrySet()) {
                batchPollExecutor.pollCabinet(entry.getKey(), entry.getValue());
            }
            for (Map.Entry<Long, List<Long>> entry : statusCandidates.entrySet()) {
                statusReconcileExecutor.reconcileCabinet(entry.getKey(), entry.getValue(), now);
            }
            for (WbCampaignManagementState state : states) {
                if (!state.isScheduleEnabled()) {
                    continue;
                }
                try {
                    scheduleProcessor.processCampaign(state.getCampaignId(), state.getCabinetId(), now);
                } catch (Exception e) {
                    log.warn("Ошибка планировщика РК campaignId={} cabinetId={}: {}",
                            state.getCampaignId(), state.getCabinetId(), e.getMessage());
                }
            }
        } finally {
            budgetPollCoordinator.endSchedulerTick();
        }
    }
}
