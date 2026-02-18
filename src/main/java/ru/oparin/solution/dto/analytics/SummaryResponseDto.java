package ru.oparin.solution.dto.analytics;

import lombok.*;

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
     * При запросе с пагинацией (page/size) не заполняется.
     */
    private Map<Integer, AggregatedMetricsDto> aggregatedMetrics;

    /**
     * Общее количество артикулов (при пагинации — после применения поиска). Без пагинации — null.
     */
    private Long totalArticles;
}

