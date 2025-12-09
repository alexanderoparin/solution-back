package ru.oparin.solution.service.analytics;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Утилиты для математических расчетов.
 */
public class MathUtils {

    private static final BigDecimal PERCENT_MULTIPLIER = BigDecimal.valueOf(100);
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
     * Рассчитывает процент от числа для BigDecimal значений.
     * Формула: (numerator / denominator) * 100
     *
     * @param numerator числитель
     * @param denominator знаменатель
     * @return процент или null, если знаменатель равен нулю или одно из значений null
     */
    public static BigDecimal calculatePercentage(BigDecimal numerator, BigDecimal denominator) {
        if (numerator == null || denominator == null || denominator.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        return numerator
                .multiply(PERCENT_MULTIPLIER)
                .divide(denominator, SCALE, RoundingMode.HALF_UP);
    }

    /**
     * Рассчитывает процент изменения между двумя значениями.
     * Формула: ((current - previous) / previous) * 100
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
     * Рассчитывает разницу между двумя процентными значениями.
     * Для метрик, измеряемых в процентах (например, конверсия, CTR, DRR),
     * изменение показывается как разница, а не как процентное изменение.
     * Формула: current - previous
     *
     * @param current текущее значение в процентах
     * @param previous предыдущее значение в процентах
     * @return разница в процентах или null, если одно из значений null
     */
    public static BigDecimal calculatePercentageDifference(BigDecimal current, BigDecimal previous) {
        if (current == null || previous == null) {
            return null;
        }
        return current.subtract(previous)
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

    /**
     * Безопасное деление BigDecimal с округлением.
     * Возвращает null, если делитель равен нулю, чтобы избежать ArithmeticException.
     *
     * @param dividend делимое
     * @param divisor делитель
     * @return результат деления или null, если делитель равен нулю
     */
    public static BigDecimal divideSafely(BigDecimal dividend, BigDecimal divisor) {
        if (dividend == null || divisor == null || divisor.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        return dividend.divide(divisor, SCALE, RoundingMode.HALF_UP);
    }

    /**
     * Безопасное деление BigDecimal на целое число с округлением.
     *
     * @param dividend делимое
     * @param divisor делитель (целое число)
     * @return результат деления или null, если делитель равен нулю
     */
    public static BigDecimal divideSafely(BigDecimal dividend, long divisor) {
        if (dividend == null || divisor == 0) {
            return null;
        }
        return dividend.divide(BigDecimal.valueOf(divisor), SCALE, RoundingMode.HALF_UP);
    }

    /**
     * Проверяет, является ли значение положительным (больше нуля).
     *
     * @param value проверяемое значение
     * @return true, если значение не null и больше нуля
     */
    public static boolean isPositive(BigDecimal value) {
        return value != null && value.compareTo(BigDecimal.ZERO) > 0;
    }
}

