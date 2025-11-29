package ru.oparin.solution.validation;

import lombok.experimental.UtilityClass;
import org.springframework.http.HttpStatus;
import ru.oparin.solution.exception.UserException;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Валидатор для периода аналитики.
 */
@UtilityClass
public class AnalyticsPeriodValidator {

    private static final int MAX_ANALYTICS_PERIOD_DAYS = 7;

    /**
     * Валидирует период аналитики.
     *
     * @param dateFrom дата начала периода
     * @param dateTo дата окончания периода
     * @throws UserException если период невалиден
     */
    public void validate(LocalDate dateFrom, LocalDate dateTo) {
        LocalDate today = LocalDate.now();

        validateDateToNotInFuture(dateTo, today);
        validatePeriodNotExceedsMax(dateFrom, dateTo);
        validateDateFromNotTooOld(dateFrom, today);
    }

    private void validateDateToNotInFuture(LocalDate dateTo, LocalDate today) {
        if (dateTo.isAfter(today)) {
            throw new UserException(
                    "Дата окончания периода не может быть в будущем",
                    HttpStatus.BAD_REQUEST
            );
        }
    }

    private void validatePeriodNotExceedsMax(LocalDate dateFrom, LocalDate dateTo) {
        long daysBetween = ChronoUnit.DAYS.between(dateFrom, dateTo);
        if (daysBetween > MAX_ANALYTICS_PERIOD_DAYS) {
            throw new UserException(
                    String.format("Период не может превышать %d дней. Запрошенный период: %d дней", 
                            MAX_ANALYTICS_PERIOD_DAYS, daysBetween),
                    HttpStatus.BAD_REQUEST
            );
        }
    }

    private void validateDateFromNotTooOld(LocalDate dateFrom, LocalDate today) {
        LocalDate minDate = today.minusDays(MAX_ANALYTICS_PERIOD_DAYS);
        if (dateFrom.isBefore(minDate)) {
            throw new UserException(
                    String.format("Дата начала периода не может быть раньше, чем %d дней назад. Минимальная дата: %s", 
                            MAX_ANALYTICS_PERIOD_DAYS, minDate),
                    HttpStatus.BAD_REQUEST
            );
        }
    }
}

