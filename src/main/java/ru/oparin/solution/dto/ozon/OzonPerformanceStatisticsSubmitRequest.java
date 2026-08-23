package ru.oparin.solution.dto.ozon;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * Тело POST /api/client/statistics — запрос async-отчёта по кампаниям.
 */
@Getter
@Setter
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class OzonPerformanceStatisticsSubmitRequest {

    private List<Long> campaigns;

    private String dateFrom;

    private String dateTo;

    @Builder.Default
    private String groupBy = "DATE";
}
