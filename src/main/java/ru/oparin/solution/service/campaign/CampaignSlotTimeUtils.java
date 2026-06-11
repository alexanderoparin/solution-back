package ru.oparin.solution.service.campaign;

import java.time.LocalTime;

/**
 * Утилиты времени слотов (шаг 30 минут).
 * Окончание в 23:59 — конец суток (полуинтервал [start, конец дня)).
 */
public final class CampaignSlotTimeUtils {

    private static final int STEP_MINUTES = 30;
    private static final int END_OF_DAY_MINUTES = 24 * 60;
    private static final LocalTime END_OF_DAY = LocalTime.of(23, 59);

    private CampaignSlotTimeUtils() {
    }

    public static LocalTime snap(LocalTime time) {
        if (time == null) {
            return LocalTime.MIDNIGHT;
        }
        if (isEndOfDay(time)) {
            return END_OF_DAY;
        }
        int total = time.toSecondOfDay() / 60;
        int snapped = (total / STEP_MINUTES) * STEP_MINUTES;
        if (snapped >= END_OF_DAY_MINUTES - 1) {
            snapped = END_OF_DAY_MINUTES - STEP_MINUTES;
        }
        return LocalTime.of(snapped / 60, snapped % 60);
    }

    public static LocalTime parseStartHHmm(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Время не указано");
        }
        String trimmed = value.trim();
        if (isEndOfDayLabel(trimmed)) {
            throw new IllegalArgumentException("23:59 допустимо только как окончание слота");
        }
        return snap(LocalTime.parse(trimmed));
    }

    /**
     * Парсит время окончания; «23:59» — конец суток.
     */
    public static LocalTime parseEndHHmm(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Время не указано");
        }
        if (isEndOfDayLabel(value.trim())) {
            return END_OF_DAY;
        }
        return snap(LocalTime.parse(value.trim()));
    }

    /** @deprecated используйте {@link #parseStartHHmm} или {@link #parseEndHHmm} */
    public static LocalTime parseHHmm(String value) {
        return parseStartHHmm(value);
    }

    public static String format(LocalTime time) {
        if (isEndOfDay(time)) {
            return "23:59";
        }
        return snap(time).toString().substring(0, 5);
    }

    /** Форматирует окончание слота для API. */
    public static String formatEnd(LocalTime start, LocalTime end) {
        return format(end);
    }

    public static int toMinutes(LocalTime time) {
        return time.getHour() * 60 + time.getMinute();
    }

    public static int endMinutes(LocalTime start, LocalTime end) {
        if (isEndOfDay(end)) {
            return END_OF_DAY_MINUTES;
        }
        return toMinutes(end);
    }

    public static boolean isEndAfterStart(LocalTime start, LocalTime end) {
        return endMinutes(start, end) > toMinutes(start);
    }

    /**
     * Активен ли момент времени в слоте [start, end).
     */
    public static boolean containsTime(LocalTime time, LocalTime start, LocalTime end) {
        int t = toMinutes(time);
        return t >= toMinutes(start) && t < endMinutes(start, end);
    }

    /**
     * Пересечение полуинтервалов [start, end) — конец слота не включается.
     */
    public static boolean overlaps(LocalTime start1, LocalTime end1, LocalTime start2, LocalTime end2) {
        int s1 = toMinutes(start1);
        int e1 = endMinutes(start1, end1);
        int s2 = toMinutes(start2);
        int e2 = endMinutes(start2, end2);
        return s1 < e2 && s2 < e1;
    }

    public static LocalTime defaultHalfHourFrom(LocalTime start) {
        LocalTime s = snap(start);
        LocalTime end = s.plusMinutes(STEP_MINUTES);
        if (!end.isAfter(s)) {
            return END_OF_DAY;
        }
        return end;
    }

    private static boolean isEndOfDay(LocalTime time) {
        return time.equals(END_OF_DAY);
    }

    private static boolean isEndOfDayLabel(String value) {
        return "23:59".equals(value);
    }
}
