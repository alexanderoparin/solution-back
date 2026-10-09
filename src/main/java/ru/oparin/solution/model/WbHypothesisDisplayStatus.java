package ru.oparin.solution.model;

/**
 * Отображаемый статус гипотезы (считается автоматически по датам и данным).
 */
public enum WbHypothesisDisplayStatus {
    /** Подготовка к тесту. */
    PREPARATION,
    /** Тест запущен / идёт период проверки. */
    IN_PROGRESS,
    /** Период закончился, данных ещё недостаточно. */
    WAITING_DATA,
    /** Успешно. */
    SUCCESS,
    /** Неуспешно. */
    FAILURE
}
