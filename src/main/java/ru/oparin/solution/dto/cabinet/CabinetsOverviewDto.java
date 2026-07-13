package ru.oparin.solution.dto.cabinet;

import lombok.Builder;

import java.util.List;

@Builder
public record CabinetsOverviewDto(
        List<OwnedCabinetRowDto> owned,
        List<GrantedCabinetRowDto> granted
) {
}
