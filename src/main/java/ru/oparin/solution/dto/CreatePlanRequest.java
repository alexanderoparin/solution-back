package ru.oparin.solution.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreatePlanRequest {

    @NotBlank(message = "Название обязательно")
    private String name;

    private String description;

    @NotNull
    @DecimalMin("0")
    private BigDecimal priceRub;

    @NotNull
    private Integer periodDays;

    private Integer maxCabinets;

    @Builder.Default
    private Integer sortOrder = 0;

    @Builder.Default
    private Boolean isActive = true;
}
