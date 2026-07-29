package ru.oparin.solution.model;

/**
 * Статус А/Б-теста главного фото.
 */
public enum AbTestStatus {
    /** Создан, ожидается асинхронная загрузка медиа на WB. */
    PENDING_START,
    /** Тест активен, идёт ротация и сбор статистики. */
    ENABLED,
    /** Тест остановлен (вручную или автоматически). */
    DISABLED
}
