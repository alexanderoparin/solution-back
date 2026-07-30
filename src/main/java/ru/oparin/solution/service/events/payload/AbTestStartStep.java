package ru.oparin.solution.service.events.payload;

/**
 * Шаг асинхронного старта А/Б-теста (каждый шаг — отдельное событие очереди).
 */
public enum AbTestStartStep {
    /** Получить галерею карточки и сохранить control локально. */
    RESOLVE_CARD,
    /** Загрузить один вариант во временный слот media/file. */
    UPLOAD_VARIANT,
    /** Обновить photoUrl/previewUrl вариантов из Content API. */
    REFRESH_URLS,
    /** Вернуть исходный набор медиа через media/save. */
    RESTORE_GALLERY,
    /** Выставить control в слот 1 и перевести тест в ENABLED. */
    APPLY_CONTROL
}
