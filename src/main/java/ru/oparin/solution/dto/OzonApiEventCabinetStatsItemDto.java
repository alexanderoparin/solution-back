package ru.oparin.solution.dto;

import lombok.Builder;

@Builder
public record OzonApiEventCabinetStatsItemDto(
        Long cabinetId,
        String cabinetName,
        long count
) {
}
