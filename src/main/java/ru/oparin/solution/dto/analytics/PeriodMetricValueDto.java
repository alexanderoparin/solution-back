package ru.oparin.solution.dto.analytics;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * DTO для значения метрики по периоду.
 * Содержит значение метрики за конкретный период и процент изменения относительно предыдущего периода.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PeriodMetricValueDto {
    /**
     * Идентификатор периода.
     */
    private Integer periodId;
    
    /**
     * Значение метрики. Может быть Integer, BigDecimal или null.
     */
    private Object value;
    
    /**
     * Процент изменения значения относительно предыдущего периода. Может быть null, если нет данных для сравнения.
     */
    private BigDecimal changePercent;
}
