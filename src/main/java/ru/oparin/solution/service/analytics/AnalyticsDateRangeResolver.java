package ru.oparin.solution.service.analytics;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Нормализация периода для {@code dailyData} и списка рекламных кампаний.
 */
public final class AnalyticsDateRangeResolver {

    /** Дней в суточной выборке по умолчанию (вчера и ещё 13 дней назад). */
    public static final int DEFAULT_DAILY_DATA_SPAN_DAYS = 14;

    /** Максимум календарных дней в одном запросе (защита от слишком тяжёлых выборок). */
    public static final int MAX_DAILY_DATA_SPAN_DAYS = 120;

    private AnalyticsDateRangeResolver() {
    }

    /**
     * Конец не позже вчера, длина ограничена {@link #MAX_DAILY_DATA_SPAN_DAYS}.
     * Если {@code from}/{@code to} не заданы — последние {@link #DEFAULT_DAILY_DATA_SPAN_DAYS} дней до вчера.
     */
    public static AnalyticsDateRange resolve(LocalDate from, LocalDate to) {
        LocalDate yesterday = LocalDate.now().minusDays(1);
        LocalDate startDate;
        LocalDate endDate;
        if (from != null && to != null) {
            LocalDate a = from;
            LocalDate b = to;
            if (a.isAfter(b)) {
                LocalDate tmp = a;
                a = b;
                b = tmp;
            }
            endDate = b.isAfter(yesterday) ? yesterday : b;
            startDate = a;
            if (startDate.isAfter(endDate)) {
                startDate = endDate;
            }
            long span = ChronoUnit.DAYS.between(startDate, endDate) + 1;
            if (span > MAX_DAILY_DATA_SPAN_DAYS) {
                startDate = endDate.minusDays(MAX_DAILY_DATA_SPAN_DAYS - 1);
            }
        } else {
            endDate = yesterday;
            startDate = endDate.minusDays(DEFAULT_DAILY_DATA_SPAN_DAYS - 1);
        }
        return new AnalyticsDateRange(startDate, endDate);
    }

    /**
     * Нормализованный период: начало и конец включительно.
     */
    public record AnalyticsDateRange(LocalDate startDate, LocalDate endDate) {
    }
}
