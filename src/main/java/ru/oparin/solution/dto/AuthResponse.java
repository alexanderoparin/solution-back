package ru.oparin.solution.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * DTO ответа при успешной авторизации.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponse {
    /**
     * JWT токен для аутентификации.
     */
    private String token;

    /**
     * Email пользователя.
     */
    private String email;

    /**
     * Роль пользователя.
     */
    private String role;

    /**
     * ID пользователя.
     */
    private Long userId;

    /**
     * Флаг временного пароля (требует смены при первом входе).
     */
    private Boolean isTemporaryPassword;
}

