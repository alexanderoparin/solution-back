package ru.oparin.solution.dto.analytics.manage;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Запрос на единоразовое пополнение бюджета РК.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CampaignManualTopUpRequestDto {

    /** Сумма пополнения, ₽. */
    @NotNull(message = "Укажите сумму пополнения")
    private Integer topUpAmount;

    /** Источник средств WB: 0 — счёт, 1 — баланс, 3 — бонусы. */
    @NotNull(message = "Укажите источник пополнения")
    private Integer sourceType;
}
