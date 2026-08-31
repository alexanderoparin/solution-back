package ru.oparin.solution.dto.analytics;

import lombok.Builder;
import lombok.Value;

import java.util.List;

/**
 * Параметры сводной аналитики и группы метрик (без User/cabinetId — они приходят из контекста доступа).
 */
@Value
@Builder
public class AnalyticsSummaryRequest {

    List<PeriodDto> periods;
    List<Long> excludedNmIds;
    Integer page;
    Integer size;
    String search;
    List<Long> includedNmIds;
    Boolean filterToNone;
    Boolean onlyWithPhoto;
    Boolean onlyPriority;
    Boolean onlyInAdvertising;
    String sortBy;
    String sortDir;

    /**
     * Собирает запрос из HTTP DTO сводной аналитики.
     */
    public static AnalyticsSummaryRequest from(SummaryRequestDto request) {
        return AnalyticsSummaryRequest.builder()
                .periods(request.getPeriods())
                .excludedNmIds(request.getExcludedNmIds())
                .page(request.getPage())
                .size(request.getSize())
                .search(request.getSearch())
                .includedNmIds(request.getIncludedNmIds())
                .filterToNone(request.getFilterToNone())
                .onlyWithPhoto(request.getOnlyWithPhoto())
                .onlyPriority(request.getOnlyPriority())
                .onlyInAdvertising(request.getOnlyInAdvertising())
                .sortBy(request.getSortBy())
                .sortDir(request.getSortDir())
                .build();
    }
}
