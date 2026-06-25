package ru.oparin.solution.service.campaign;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ru.oparin.solution.model.CampaignManagementState;
import ru.oparin.solution.repository.CampaignManagementStateRepository;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

/**
 * Планировщик: расписание слотов, лимит бюджета слота, автопополнение.
 * Каждая кампания обрабатывается в отдельной транзакции ({@link CampaignScheduleProcessor}).
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class CampaignScheduleOrchestrator {

    private static final ZoneId ZONE = ZoneId.of("Europe/Moscow");

    private final CampaignManagementStateRepository stateRepository;
    private final CampaignScheduleProcessor scheduleProcessor;
    private final CampaignSchedulePollPlanner pollPlanner;
    private final CabinetBudgetPollCoordinator budgetPollCoordinator;

    @Scheduled(cron = "0 * * * * *")
    @SchedulerLock(name = "campaignScheduleOrchestrator", lockAtLeastFor = "30s", lockAtMostFor = "55s")
    public void tick() {
        List<CampaignManagementState> states = stateRepository.findAll();
        ZonedDateTime now = ZonedDateTime.now(ZONE);
        Map<Long, List<Long>> pollCandidates = pollPlanner.collectBudgetPollCandidates(states, now);
        budgetPollCoordinator.beginSchedulerTick(pollCandidates);
        try {
            for (CampaignManagementState state : states) {
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
