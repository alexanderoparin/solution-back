package ru.oparin.solution.service.analytics;

import ru.oparin.solution.dto.analytics.PeriodDto;
import ru.oparin.solution.util.PeriodGenerator;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Сравнение метрик между соседними периодами и вспомогательные операции над периодами.
 */
public final class AnalyticsPercentChange {

    private AnalyticsPercentChange() {
    }

    /**
     * Проверяет, что у каждого периода dateFrom не позже dateTo.
     */
    public static void validatePeriods(List<PeriodDto> periods) {
        if (!PeriodGenerator.validatePeriods(periods)) {
            throw new IllegalArgumentException(
                    "Периоды некорректны: дата начала периода не может быть позже даты окончания");
        }
    }

    /**
     * Сортирует периоды по dateFrom (слева направо: старый → новый).
     */
    public static List<PeriodDto> sortPeriodsByDateFrom(List<PeriodDto> periods) {
        return periods.stream()
                .sorted(Comparator.comparing(PeriodDto::getDateFrom))
                .collect(Collectors.toList());
    }

    /**
     * Предыдущий период по хронологическому порядку. {@code allPeriodsSortedByDate} должен быть отсортирован по dateFrom.
     */
    public static PeriodDto findPreviousPeriodByDateOrder(PeriodDto currentPeriod, List<PeriodDto> allPeriodsSortedByDate) {
        int idx = -1;
        for (int i = 0; i < allPeriodsSortedByDate.size(); i++) {
            if (Objects.equals(allPeriodsSortedByDate.get(i).getId(), currentPeriod.getId())) {
                idx = i;
                break;
            }
        }
        if (idx <= 0) {
            return null;
        }
        return allPeriodsSortedByDate.get(idx - 1);
    }

    /**
     * Изменение метрики: для процентных — разница, иначе процент изменения.
     *
     * @return {@code null}, если текущее значение отсутствует
     */
    public static BigDecimal between(String metricName, Object currentValue, Object previousValue) {
        if (currentValue == null) {
            return null;
        }
        if (MetricNames.isPercentageMetric(metricName)) {
            return MathUtils.calculatePercentageDifference(toBigDecimal(currentValue), toBigDecimal(previousValue));
        }
        return MathUtils.calculatePercentageChange(toBigDecimal(currentValue), toBigDecimal(previousValue));
    }

    /**
     * Категория метрики для API: funnel / advertising / unknown.
     */
    public static String metricCategory(String metricName) {
        if (MetricNames.isFunnelMetric(metricName)) {
            return "funnel";
        }
        if (MetricNames.isAdvertisingMetric(metricName)) {
            return "advertising";
        }
        return "unknown";
    }

    private static BigDecimal toBigDecimal(Object value) {
        if (value == null) {
            return null;
        }
        return switch (value) {
            case BigDecimal bd -> bd;
            case Integer i -> BigDecimal.valueOf(i);
            case Long l -> BigDecimal.valueOf(l);
            case Double d -> BigDecimal.valueOf(d);
            default -> null;
        };
    }
}
