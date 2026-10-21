package ru.oparin.solution.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * Запрос расчёта доплаты за апгрейд тира пакета.
 */
@Getter
@Setter
public class UpgradePackageQuoteRequest {

    /** Id платежа текущего пакета. */
    @NotNull(message = "Укажите paymentId пакета")
    private Long paymentId;

    /** Целевой план (тот же kind и период, больший max). */
    @NotNull(message = "Укажите targetPlanId")
    private Long targetPlanId;
}
