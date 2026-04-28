package ru.oparin.solution.dto;

import lombok.Builder;

@Builder
public record WbApiEventCabinetStatsItemDto(
        Long cabinetId,
        String cabinetName,
        long count
) {
}
