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
    /** Порядок сортировки (для админки). */
    private Integer sortOrder;
    /** Активен ли план (для админки). */
    private Boolean isActive;
    private String code;
    private String productCode;
    /** DAYS или CALENDAR_MONTH. */
    private String periodType;
}
