package ru.oparin.solution.dto;

import lombok.Builder;

import java.util.Map;

@Builder
public record OzonApiEventTypeStatsDto(
        String baseStatus,
        long total,
        Map<String, Long> byType
) {
}
