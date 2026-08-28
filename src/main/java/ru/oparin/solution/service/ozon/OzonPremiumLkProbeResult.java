package ru.oparin.solution.service.ozon;

/**
 * Результат probe Premium в ЛК Ozon через {@code /v1/analytics/data}.
 */
public enum OzonPremiumLkProbeResult {

    /** Доступна аналитика старше 3 месяцев — платный Premium в ЛК. */
    HAS_PREMIUM,

    /** Запрос за период &gt;3 месяцев отклонён — без Premium в ЛК. */
    NO_PREMIUM,

    /** Не удалось определить (ошибка API, нет baseline и т.п.). */
    INCONCLUSIVE
}
