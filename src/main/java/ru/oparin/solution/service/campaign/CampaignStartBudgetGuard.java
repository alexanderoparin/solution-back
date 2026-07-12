package ru.oparin.solution.service.campaign;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.oparin.solution.model.CampaignManagementState;
import ru.oparin.solution.repository.CampaignManagementStateRepository;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * Блокирует повторные попытки запуска РК по расписанию, пока на WB нет бюджета для старта.
 */
@Service
@RequiredArgsConstructor
public class CampaignStartBudgetGuard {

    private static final ZoneId SCHEDULE_ZONE = ZoneId.of("Europe/Moscow");
    private static final long NO_BUDGET_RECHECK_INTERVAL_HOURS = 1;

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
     * {@code true}, если пора снова проверить бюджет для запуска (первый раз в слоте или прошёл час).
     */
    public boolean isNoBudgetRecheckDue(CampaignManagementState state, ZonedDateTime now) {
        if (state == null || state.getStartNoBudgetCheckedAt() == null) {
            return true;
        }
        LocalDateTime nextCheckAt = state.getStartNoBudgetCheckedAt().plusHours(NO_BUDGET_RECHECK_INTERVAL_HOURS);
        return !now.withZoneSameInstant(SCHEDULE_ZONE).toLocalDateTime().isBefore(nextCheckAt);
    }

    /**
     * Фиксирует время проверки бюджета для запуска.
     */
    public void markNoBudgetChecked(CampaignManagementState state, ZonedDateTime now) {
        if (state != null) {
            state.setStartNoBudgetCheckedAt(now.withZoneSameInstant(SCHEDULE_ZONE).toLocalDateTime());
        }
    }

    /**
     * Сбрасывает блокировку и таймер при входе в новый слот.
     */
    public void resetForNewSlot(CampaignManagementState state) {
        if (state == null) {
            return;
        }
        state.setStartBlockedNoBudget(false);
        state.setStartNoBudgetCheckedAt(null);
    }

    /**
     * Блокирует запуск по расписанию и пишет в историю (один раз до снятия блокировки).
     */
    public void blockStartDueToNoBudget(CampaignManagementState state, Long advertId, Long cabinetId, ZonedDateTime now) {
        if (state == null) {
            return;
        }
        markNoBudgetChecked(state, now);
        if (state.isStartBlockedNoBudget()) {
            return;
        }
        state.setStartBlockedNoBudget(true);
        scheduleControlNotifier.onStartBlockedNoBudget(advertId, cabinetId);
    }

    /**
     * Блокирует запуск по расписанию (исполнитель очереди WB API, отдельная транзакция).
     */
    public void blockStartDueToNoBudget(Long advertId, Long cabinetId) {
        ZonedDateTime now = ZonedDateTime.now(SCHEDULE_ZONE);
        stateRepository.findById(advertId).ifPresent(state -> {
            blockStartDueToNoBudget(state, advertId, cabinetId, now);
            stateRepository.save(state);
        });
    }

    /**
     * Снимает блокировку после пополнения бюджета.
     */
    public void clearBlockIfBudgetAvailable(CampaignManagementState state, int budgetTotal) {
        if (state != null && budgetTotal > 0 && state.isStartBlockedNoBudget()) {
            state.setStartBlockedNoBudget(false);
            state.setStartNoBudgetCheckedAt(null);
        }
    }
}
