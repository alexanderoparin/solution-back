package ru.oparin.solution.dto;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

/**
 * Заявка на индивидуальный тариф (свыше 10 кабинетов).
 */
@Getter
@Setter
public class CustomTariffRequestDto {

    /** Желаемое количество кабинетов (от 11). */
    @NotNull(message = "Укажите количество кабинетов")
    @Min(value = 11, message = "Укажите не меньше 11 кабинетов")
    @Max(value = 10_000, message = "Слишком большое число кабинетов")
    private Integer cabinetsCount;

    /** Telegram для связи. */
    @Size(max = 64, message = "Telegram слишком длинный")
    private String telegram;

    /** Телефон для связи. */
    @Size(max = 32, message = "Телефон слишком длинный")
    private String phone;

    /** Текст заявки. */
    @NotBlank(message = "Напишите сообщение")
    @Size(max = 4000, message = "Сообщение слишком длинное")
    private String message;
}
