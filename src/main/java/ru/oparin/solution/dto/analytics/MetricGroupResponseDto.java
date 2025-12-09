package ru.oparin.solution.dto.analytics;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * DTO для ответа детальных метрик по группе.
 * Содержит значения метрики для всех артикулов или кампаний.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MetricGroupResponseDto {
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
     * Список значений метрики по артикулам. Используется для метрик воронки.
     */
    private List<ArticleMetricDto> articles;
    
    /**
     * Список значений метрики по рекламным кампаниям. Используется для рекламных метрик.
     */
    private List<CampaignMetricDto> campaigns;
}

