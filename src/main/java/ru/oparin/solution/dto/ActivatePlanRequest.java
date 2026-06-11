package ru.oparin.solution.dto;

import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ActivatePlanRequest {

    @NotNull
    private Long planId;
}
