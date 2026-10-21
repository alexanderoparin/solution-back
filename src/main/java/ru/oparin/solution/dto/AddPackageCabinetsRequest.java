package ru.oparin.solution.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * Добор кабинетов в уже оплаченный пакет (до max тира, без оплаты).
 */
@Getter
@Setter
public class AddPackageCabinetsRequest {

    /** Id платежа, объединяющего пакет. */
    @NotNull(message = "Укажите paymentId пакета")
    private Long paymentId;

    /** Кабинеты для добавления. */
    @NotEmpty(message = "Выберите хотя бы один кабинет")
    private List<Long> cabinetIds;
}
