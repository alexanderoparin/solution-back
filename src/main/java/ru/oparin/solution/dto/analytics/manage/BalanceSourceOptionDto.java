package ru.oparin.solution.dto.analytics.manage;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BalanceSourceOptionDto {
    private int type;
    private String label;
    private Integer availableRub;
}
