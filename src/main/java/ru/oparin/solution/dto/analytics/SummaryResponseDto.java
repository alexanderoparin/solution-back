package ru.oparin.solution.dto.analytics;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * DTO для ответа сводной страницы аналитики.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SummaryResponseDto {
    private GeneralInfoDto generalInfo;
    private List<PeriodDto> periods;
    private List<ArticleSummaryDto> articles;
    private Map<Integer, AggregatedMetricsDto> aggregatedMetrics; // ключ - periodId
}

