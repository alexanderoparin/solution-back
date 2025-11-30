package ru.oparin.solution.dto.analytics;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * DTO для ответа детальных метрик по группе.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MetricGroupResponseDto {
    private String metricName; // Английский ключ (например, "cart_conversion")
    private String metricNameRu; // Русское название для отображения
    private String category; // "funnel" или "advertising"
    private List<ArticleMetricDto> articles;
}

