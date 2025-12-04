package ru.oparin.solution.dto.analytics;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * DTO для метрики конкретной рекламной кампании.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CampaignMetricDto {
    private Long campaignId;
    private String campaignName;
    private List<Long> articles; // Список артикулов (nmId) в кампании
    private List<PeriodMetricValueDto> periods; // Значения метрики по периодам
}

