package ru.oparin.solution.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * Запрос на инициацию оплаты тарифа / услуги / пакета А/Б.
 * Для MAIN/CAMPAIGN — список {@code cabinetIds}; для AB_PACK кабинеты не нужны.
 */
@Getter
@Setter
public class InitiatePaymentRequest {

    @NotNull(message = "planId обязателен")
    private Long planId;

    /**
     * Legacy: один кабинет (если cabinetIds не передан).
     */
    private Long cabinetId;

    /**
     * Кабинеты для активации (PRO / Управление РК). Для AB_PACK может быть пустым.
     */
    private List<Long> cabinetIds;
}
