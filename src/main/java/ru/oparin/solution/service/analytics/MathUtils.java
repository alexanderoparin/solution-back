package ru.oparin.solution.service.analytics;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Утилиты для математических расчетов.
 */
public class MathUtils {

    private static final BigDecimal PERCENT_MULTIPLIER = BigDecimal.valueOf(100);
    private static final BigDecimal KOPECKS_TO_RUBLES = BigDecimal.valueOf(100);
    private static final int SCALE = 2;
    private static final int PERCENTAGE_SCALE = 4;

    /**
     * Рассчитывает процент от числа.
     */
    public static BigDecimal calculatePercentage(int numerator, int denominator) {
        return calculatePercentage((long) numerator, (long) denominator);
    }

    /**
     * Рассчитывает процент от числа.
     */
    public static BigDecimal calculatePercentage(long numerator, long denominator) {
        if (denominator == 0) {
            return null;
        }
        return BigDecimal.valueOf(numerator)
                .multiply(PERCENT_MULTIPLIER)
                .divide(BigDecimal.valueOf(denominator), SCALE, RoundingMode.HALF_UP);
    }

    /**
     * Конвертирует копейки в рубли.
     */
    public static BigDecimal convertKopecksToRubles(long kopecks) {
        return BigDecimal.valueOf(kopecks)
                .divide(KOPECKS_TO_RUBLES, SCALE, RoundingMode.HALF_UP);
    }

    /**
     * Делит копейки на значение и возвращает результат в рублях.
     */
    public static BigDecimal divideKopecksByValue(long kopecks, int divisor) {
        if (divisor == 0) {
            return null;
        }
        return convertKopecksToRubles(kopecks)
                .divide(BigDecimal.valueOf(divisor), SCALE, RoundingMode.HALF_UP);
    }

    /**
     * Рассчитывает процент изменения между двумя значениями.
     */
    public static BigDecimal calculatePercentageChange(BigDecimal current, BigDecimal previous) {
        if (current == null || previous == null || previous.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        return current.subtract(previous)
                .divide(previous, PERCENTAGE_SCALE, RoundingMode.HALF_UP)
                .multiply(PERCENT_MULTIPLIER)
                .setScale(SCALE, RoundingMode.HALF_UP);
    }

    /**
     * Возвращает значение или ноль, если значение null.
     */
    public static int getValueOrZero(Integer value) {
        return value != null ? value : 0;
    }

    /**
     * Возвращает значение или ноль, если значение null.
     */
    public static long getValueOrZero(Long value) {
        return value != null ? value : 0L;
    }

    /**
     * Возвращает значение или ноль, если значение null.
     */
    public static BigDecimal getValueOrZero(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }
}

