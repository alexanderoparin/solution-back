package ru.oparin.solution.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * Запрос на инициацию оплаты тарифного плана.
 */
@Getter
@Setter
public class InitiatePaymentRequest {

    @NotNull(message = "planId обязателен")
    private Long planId;
}
