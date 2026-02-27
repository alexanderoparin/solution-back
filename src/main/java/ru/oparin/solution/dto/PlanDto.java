package ru.oparin.solution.dto;

import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlanDto {

    private Long id;
    private String name;
    private String description;
    private BigDecimal priceRub;
    private Integer periodDays;
    private Integer maxCabinets;
}
