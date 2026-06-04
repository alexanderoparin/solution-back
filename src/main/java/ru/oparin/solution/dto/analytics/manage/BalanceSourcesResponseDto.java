package ru.oparin.solution.dto.analytics.manage;

import lombok.*;

import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BalanceSourcesResponseDto {
    private List<BalanceSourceOptionDto> sources;
}
