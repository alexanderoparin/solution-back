package ru.oparin.solution.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class InitiatePaymentRequest {

    @NotNull(message = "ID плана обязателен")
    private Long planId;
}
