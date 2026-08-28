package ru.oparin.solution.util;

import java.time.LocalDate;

/**
 * Стандартные периоды синхронизации данных WB.
 */
public final class WbSyncPeriods {

    /** Период синка воронки продаж через WB API (лимит API — 7 дней за запрос). */
    public static final int FUNNEL_DAYS = 7;

    /** Период прочих этапов полного обновления (item-rating, календарь акций и т.д.). */
    public static final int MAIN_SYNC_DAYS = 14;

    /** Период синка рекламной статистики (fullstats). */
    public static final int PROMOTION_DAYS = 28;

    private WbSyncPeriods() {
    }

    /**
     * Начало периода воронки относительно {@code dateTo} (включительно).
     */
    public static LocalDate funnelFrom(LocalDate dateTo) {
        return dateTo.minusDays(FUNNEL_DAYS - 1L);
    }

    /**
     * Начало основного периода синка (не воронка/fullstats).
     */
    public static LocalDate mainSyncFrom(LocalDate dateTo) {
        return dateTo.minusDays(MAIN_SYNC_DAYS - 1L);
    }

    /**
     * Начало периода fullstats относительно {@code dateTo} (включительно).
     */
    public static LocalDate promotionFrom(LocalDate dateTo) {
        return dateTo.minusDays(PROMOTION_DAYS - 1L);
    }
}
