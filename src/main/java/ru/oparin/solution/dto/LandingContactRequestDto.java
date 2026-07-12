package ru.oparin.solution.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * Заявка с лендинга: имя, Telegram и согласие на обработку данных.
 */
@Getter
@Setter
public class LandingContactRequestDto {

    /** Имя заявителя. */
    @NotBlank(message = "Укажите имя")
    @Size(max = 120, message = "Имя слишком длинное")
    private String name;

    /** Telegram-аккаунт для связи. */
    @NotBlank(message = "Укажите Telegram")
    @Size(max = 64, message = "Telegram слишком длинный")
    private String telegram;

    /** Согласие на обработку персональных данных. */
    @AssertTrue(message = "Необходимо согласие на обработку персональных данных")
    private Boolean agreeToPrivacy;
}
