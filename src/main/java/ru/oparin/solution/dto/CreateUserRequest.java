package ru.oparin.solution.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import ru.oparin.solution.model.Role;

/**
 * DTO для создания нового пользователя.
 */
@Getter
@Setter
public class CreateUserRequest {

    /**
     * Email пользователя.
     */
    @NotBlank(message = "Email обязателен")
    @Email(message = "Некорректный формат email")
    private String email;

    /**
     * Временный пароль пользователя.
     */
    @NotBlank(message = "Пароль обязателен")
    @Size(min = 6, message = "Пароль должен содержать минимум 6 символов")
    private String password;

    /**
     * Роль пользователя.
     */
    @NotNull(message = "Роль обязательна")
    private Role role;
}

