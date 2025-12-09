package ru.oparin.solution.dto.analytics;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * DTO для метрики конкретной рекламной кампании.
 * Используется для отображения рекламных метрик по кампаниям.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CampaignMetricDto {
    /**
     * ID рекламной кампании (advert_id).
     */
    private Long campaignId;
    
    /**
     * Название рекламной кампании.
     */
    private String campaignName;
    
    /**
     * Список артикулов (nmId), участвующих в кампании.
     */
    private List<Long> articles;
    
    /**
     * Список значений метрики по периодам.
     */
    private List<PeriodMetricValueDto> periods;
}

