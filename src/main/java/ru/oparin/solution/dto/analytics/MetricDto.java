package ru.oparin.solution.dto.analytics;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * DTO для метрики на странице артикула.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MetricDto {
    private String metricName; // Английский ключ (например, "cart_conversion")
    private String metricNameRu; // Русское название для отображения
    private String category; // "funnel" или "advertising"
    private List<PeriodMetricValueDto> periods;
}

