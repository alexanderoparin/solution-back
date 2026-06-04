package ru.oparin.solution.service.campaign;

import java.time.LocalTime;

/**
 * Утилиты времени слотов (шаг 30 минут).
 */
public final class CampaignSlotTimeUtils {

    private static final int STEP_MINUTES = 30;

    private CampaignSlotTimeUtils() {
    }

    public static LocalTime snap(LocalTime time) {
        if (time == null) {
            return LocalTime.MIDNIGHT;
        }
        int total = time.toSecondOfDay() / 60;
        int snapped = (total / STEP_MINUTES) * STEP_MINUTES;
        if (snapped >= 24 * 60) {
            snapped = 24 * 60 - STEP_MINUTES;
        }
        return LocalTime.of(snapped / 60, snapped % 60);
    }

    public static LocalTime parseHHmm(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Время не указано");
        }
        return snap(LocalTime.parse(value.trim()));
    }

    public static String format(LocalTime time) {
        return snap(time).toString().substring(0, 5);
    }

    public static LocalTime defaultHalfHourFrom(LocalTime start) {
        LocalTime s = snap(start);
        LocalTime end = s.plusMinutes(STEP_MINUTES);
        if (end.equals(LocalTime.MIDNIGHT) || !end.isAfter(s)) {
            return LocalTime.of(0, STEP_MINUTES);
        }
        return end;
    }
}
