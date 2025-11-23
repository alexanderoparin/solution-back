package ru.oparin.solution.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * DTO для регистрации продавца.
 */
@Getter
@Setter
public class RegisterRequest {

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
    @Size(min = 8, message = "Пароль должен содержать минимум 8 символов")
    private String password;

    /**
     * WB API ключ продавца.
     */
    @NotBlank(message = "WB API ключ обязателен")
    @Size(min = 10, message = "WB API ключ должен содержать минимум 10 символов")
    private String wbApiKey;
}

