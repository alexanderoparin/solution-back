package ru.oparin.solution.dto.analytics;

/**
 * Результат попытки запуска или паузы РК по расписанию.
 */
public record ScheduleControlAttemptResult(
        ScheduleControlOutcome outcome,
        Long eventId,
        String message
) {
    public static ScheduleControlAttemptResult directSuccess() {
        return new ScheduleControlAttemptResult(ScheduleControlOutcome.DIRECT_SUCCESS, null, null);
    }

    public static ScheduleControlAttemptResult enqueued(Long eventId) {
        return new ScheduleControlAttemptResult(ScheduleControlOutcome.ENQUEUED, eventId, null);
    }

    public static ScheduleControlAttemptResult skippedPending() {
        return new ScheduleControlAttemptResult(ScheduleControlOutcome.SKIPPED_ALREADY_PENDING, null, null);
    }

    public static ScheduleControlAttemptResult failed(String message) {
        return new ScheduleControlAttemptResult(ScheduleControlOutcome.FAILED, null, message);
    }
}
