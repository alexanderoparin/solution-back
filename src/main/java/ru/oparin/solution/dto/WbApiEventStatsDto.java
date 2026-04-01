package ru.oparin.solution.dto;

import lombok.Builder;

import java.util.Map;

@Builder
public record WbApiEventStatsDto(
        long total,
        Map<String, Long> byStatus
) {
}
