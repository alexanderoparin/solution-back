package ru.oparin.solution.dto.analytics;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * DTO для ответа страницы артикула.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArticleResponseDto {
    private ArticleDetailDto article;
    private List<PeriodDto> periods;
    private List<MetricDto> metrics; // все 13 метрик
    private List<DailyDataDto> dailyData;
    private List<CampaignDto> campaigns;
}

