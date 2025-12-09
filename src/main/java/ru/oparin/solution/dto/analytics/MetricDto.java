package ru.oparin.solution.dto.analytics;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * DTO для метрики на странице артикула.
 * Содержит информацию о метрике и её значения по периодам.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MetricDto {
    /**
     * Английский ключ метрики (например, "cart_conversion", "cpc").
     */
    private String metricName;
    
    /**
     * Русское название метрики для отображения на фронтенде.
     */
    private String metricNameRu;
    
    /**
     * Категория метрики: "funnel" (общая воронка), "advertising" (рекламная воронка) или "pricing" (ценообразование).
     */
    private String category;
    
    /**
     * Список значений метрики по периодам.
     */
    private List<PeriodMetricValueDto> periods;
}

