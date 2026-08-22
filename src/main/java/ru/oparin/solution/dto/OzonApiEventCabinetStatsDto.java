package ru.oparin.solution.dto;

import lombok.Builder;

import java.util.List;

@Builder
public record OzonApiEventCabinetStatsDto(
        String baseStatus,
        String baseEventType,
        long total,
        List<OzonApiEventCabinetStatsItemDto> byCabinet
) {
}
