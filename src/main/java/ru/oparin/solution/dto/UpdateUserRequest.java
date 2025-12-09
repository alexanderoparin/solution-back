package ru.oparin.solution.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/**
 * DTO для обновления пользователя.
 */
@Getter
@Setter
public class UpdateUserRequest {

    /**
     * Email пользователя.
     */
    @NotBlank(message = "Email обязателен")
    @Email(message = "Некорректный формат email")
    private String email;

    /**
     * Флаг активности пользователя.
     */
    private Boolean isActive;
}

