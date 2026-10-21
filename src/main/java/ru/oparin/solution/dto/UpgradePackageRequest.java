package ru.oparin.solution.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * Инициация оплаты апгрейда тира пакета.
 * Опционально — кабинеты, которые добавить в пакет сразу после успешной оплаты
 * (в пределах новых слотов целевого тира).
 */
@Getter
@Setter
public class UpgradePackageRequest {

    @NotNull(message = "Укажите paymentId пакета")
    private Long paymentId;

    @NotNull(message = "Укажите targetPlanId")
    private Long targetPlanId;

    /** Кабинеты для добора после апгрейда (не из текущего пакета). */
    private List<Long> cabinetIds;
}
