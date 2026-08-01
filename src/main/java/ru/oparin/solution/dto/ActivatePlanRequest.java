package ru.oparin.solution.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ActivatePlanRequest {

    @NotNull
    private Long planId;

    @NotNull
    private Long cabinetId;
}
