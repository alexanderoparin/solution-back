package ru.oparin.solution.service.campaign;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.oparin.solution.model.CampaignManagementState;
import ru.oparin.solution.repository.CampaignManagementStateRepository;

/**
 * Блокирует повторные попытки запуска РК по расписанию, пока на WB нет бюджета для старта.
 */
@Service
@RequiredArgsConstructor
public class CampaignStartBudgetGuard {

    /** Сообщение для пользователя и журнала при отсутствии бюджета. */
    public static final String NO_BUDGET_USER_MESSAGE = "нет бюджета для запуска";

    private final CampaignManagementStateRepository stateRepository;
    private final CampaignScheduleControlNotifier scheduleControlNotifier;

    /**
     * Проверяет, что ошибка WB означает отсутствие бюджета для запуска.
     */
    public static boolean isNoBudgetToStartError(String message) {
        if (message == null || message.isBlank()) {
            return false;
        }
        return message.contains("no budget to start")
                || NO_BUDGET_USER_MESSAGE.equalsIgnoreCase(message.trim());
    }

    /**
     * Блокирует запуск по расписанию и пишет в историю (один раз до снятия блокировки).
     */
    public void blockStartDueToNoBudget(CampaignManagementState state, Long advertId, Long cabinetId) {
        if (state == null || state.isStartBlockedNoBudget()) {
            return;
        }
        state.setStartBlockedNoBudget(true);
        scheduleControlNotifier.onStartBlockedNoBudget(advertId, cabinetId);
    }

    /**
     * Блокирует запуск по расписанию (исполнитель очереди WB API, отдельная транзакция).
     */
    public void blockStartDueToNoBudget(Long advertId, Long cabinetId) {
        stateRepository.findById(advertId).ifPresent(state -> {
            blockStartDueToNoBudget(state, advertId, cabinetId);
            stateRepository.save(state);
        });
    }

    /**
     * Снимает блокировку после пополнения бюджета.
     */
    public void clearBlockIfBudgetAvailable(CampaignManagementState state, int budgetTotal) {
        if (state != null && budgetTotal > 0 && state.isStartBlockedNoBudget()) {
            state.setStartBlockedNoBudget(false);
        }
    }
}
