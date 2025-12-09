package ru.oparin.solution.dto.analytics;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;
import java.util.Map;

/**
 * DTO для ответа сводной страницы аналитики.
 * Содержит список артикулов и агрегированные метрики по периодам.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SummaryResponseDto {
    /**
     * Список периодов аналитики.
     */
    private List<PeriodDto> periods;
    
    /**
     * Список артикулов с краткой информацией.
     */
    private List<ArticleSummaryDto> articles;
    
    /**
     * Агрегированные метрики по периодам. Ключ - идентификатор периода (periodId).
     */
    private Map<Integer, AggregatedMetricsDto> aggregatedMetrics;
}

