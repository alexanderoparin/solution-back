package ru.oparin.solution.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * Инициация оплаты апгрейда пакета «Управление РК» → «Полный доступ» (PRO).
 */
@Getter
@Setter
public class UpgradeToProRequest {

    @NotNull(message = "Укажите paymentId пакета")
    private Long paymentId;
}
