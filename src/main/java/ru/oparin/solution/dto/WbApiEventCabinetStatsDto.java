package ru.oparin.solution.dto;

import lombok.Builder;

import java.util.List;

@Builder
public record WbApiEventCabinetStatsDto(
        String baseStatus,
        String baseEventType,
        long total,
        List<WbApiEventCabinetStatsItemDto> byCabinet
) {
}
