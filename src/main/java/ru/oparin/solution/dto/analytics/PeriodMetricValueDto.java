package ru.oparin.solution.dto.analytics;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * DTO для значения метрики по периоду.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PeriodMetricValueDto {
    private Integer periodId;
    private Object value; // Integer, BigDecimal или null
    private BigDecimal changePercent; // Процент изменения (может быть null)
}
