package ru.oparin.solution.util;

import ru.oparin.solution.dto.analytics.PeriodDto;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Утилита для генерации периодов аналитики.
 */
public class PeriodGenerator {

    private static final int PERIOD_COUNT = 4;
    private static final int PERIOD_DAYS = 3;

    /**
     * Генерирует 4 периода по 3 дня, начиная со вчерашней даты.
     * Периоды не пересекаются.
     * 
     * @return список периодов
     */
    public static List<PeriodDto> generateDefaultPeriods() {
        LocalDate yesterday = LocalDate.now().minusDays(1);
        List<PeriodDto> periods = new ArrayList<>();

        for (int i = 0; i < PERIOD_COUNT; i++) {
            LocalDate periodEnd = yesterday.minusDays(i * PERIOD_DAYS);
            LocalDate periodStart = periodEnd.minusDays(PERIOD_DAYS - 1);

            PeriodDto period = PeriodDto.builder()
                    .id(i + 1)
                    .name("период №" + (i + 1))
                    .dateFrom(periodStart)
                    .dateTo(periodEnd)
                    .build();

            periods.add(period);
        }

        // Возвращаем периоды в обратном порядке: самый старый первым, самый новый последним
        Collections.reverse(periods);
        return periods;
    }

    /**
     * Валидирует периоды на пересечение.
     * 
     * @param periods список периодов для валидации
     * @return true если периоды не пересекаются, false если есть пересечения
     */
    public static boolean validatePeriods(List<PeriodDto> periods) {
        if (periods == null || periods.size() < 2) {
            return true;
        }

        for (int i = 0; i < periods.size(); i++) {
            PeriodDto period1 = periods.get(i);
            if (period1.getDateFrom() == null || period1.getDateTo() == null) {
                return false;
            }
            if (period1.getDateFrom().isAfter(period1.getDateTo())) {
                return false;
            }

            for (int j = i + 1; j < periods.size(); j++) {
                PeriodDto period2 = periods.get(j);
                if (period2.getDateFrom() == null || period2.getDateTo() == null) {
                    return false;
                }

                // Проверяем пересечение: период1 начинается до окончания период2
                // и период1 заканчивается после начала период2
                boolean overlaps = !period1.getDateTo().isBefore(period2.getDateFrom())
                        && !period1.getDateFrom().isAfter(period2.getDateTo());

                if (overlaps) {
                    return false;
                }
            }
        }

        return true;
    }
}

