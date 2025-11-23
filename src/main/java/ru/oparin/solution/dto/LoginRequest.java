package ru.oparin.solution.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/**
 * DTO для авторизации пользователя.
 */
@Getter
@Setter
public class LoginRequest {

    /**
     * Email пользователя.
     */
    @NotBlank(message = "Email обязателен")
    @Email(message = "Некорректный формат email")
    private String email;

    /**
     * Пароль пользователя.
     */
    @NotBlank(message = "Пароль обязателен")
    private String password;
}

