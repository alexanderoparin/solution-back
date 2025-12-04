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
     * Валидирует периоды - проверяет только корректность дат внутри каждого периода.
     * Пересечения периодов разрешены.
     * 
     * @param periods список периодов для валидации
     * @return true если все периоды корректны (дата начала не позже даты окончания), false иначе
     */
    public static boolean validatePeriods(List<PeriodDto> periods) {
        if (periods == null || periods.isEmpty()) {
            return false;
        }

        for (PeriodDto period : periods) {
            if (period.getDateFrom() == null || period.getDateTo() == null) {
                return false;
            }
            // Проверяем, что дата начала не позже даты окончания
            if (period.getDateFrom().isAfter(period.getDateTo())) {
                return false;
            }
        }

        return true;
    }
}

