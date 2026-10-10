package ru.oparin.solution.dto;

import jakarta.validation.constraints.NotNull;
import lombok.*;

/**
 * Запрос на включение/выключение промокода.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SetPromoCodeActiveRequest {

    /** Новый статус активности. */
    @NotNull(message = "Укажите статус")
    private Boolean active;
}
