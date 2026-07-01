package ru.oparin.solution.dto.analytics;

/**
 * Исход попытки запуска или паузы РК по расписанию.
 */
public enum ScheduleControlOutcome {
    /** Запрос к WB выполнен сразу (HTTP 200). */
    DIRECT_SUCCESS,
    /** Создано событие в очереди WB API. */
    ENQUEUED,
    /** Управление уже ожидает исполнения в очереди. */
    SKIPPED_ALREADY_PENDING,
    /** Ошибка без постановки в очередь (например, read-only токен). */
    FAILED
}
