package ru.oparin.solution.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

/**
 * DTO запроса на смену пароля.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChangePasswordRequest {

    /**
     * Текущий пароль.
     */
    @NotBlank(message = "Текущий пароль обязателен")
    private String currentPassword;

    /**
     * Новый пароль.
     */
    @NotBlank(message = "Новый пароль обязателен")
    @Size(min = 6, message = "Пароль должен содержать минимум 8 символов")
    private String newPassword;
}

