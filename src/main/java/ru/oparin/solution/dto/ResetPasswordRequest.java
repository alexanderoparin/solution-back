package ru.oparin.solution.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * Запрос на сброс пароля по токену из письма.
 */
@Getter
@Setter
public class ResetPasswordRequest {

    @NotBlank(message = "Токен обязателен")
    private String token;

    @NotBlank(message = "Новый пароль обязателен")
    @Size(min = 6, message = "Пароль должен содержать минимум 6 символов")
    private String newPassword;
}
