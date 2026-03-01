package ru.oparin.solution.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/**
 * Запрос на отправку ссылки для сброса пароля.
 */
@Getter
@Setter
public class ForgotPasswordRequest {

    @NotBlank(message = "Email обязателен")
    @Email(message = "Некорректный формат email")
    private String email;
}
