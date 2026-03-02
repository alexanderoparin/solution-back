package ru.oparin.solution.dto;

import jakarta.validation.constraints.DecimalMin;
import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdatePlanRequest {

    private String name;
    private String description;
    @DecimalMin("0")
    private BigDecimal priceRub;
    private Integer periodDays;
    private Integer maxCabinets;
    private Integer sortOrder;
    private Boolean isActive;
}
