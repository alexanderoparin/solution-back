package ru.oparin.solution.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import ru.oparin.solution.model.Role;

import java.time.LocalDateTime;

/**
 * DTO для профиля пользователя.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserProfileResponse {
    /**
     * ID пользователя.
     */
    private Long id;

    /**
     * Email пользователя.
     */
    private String email;

    /**
     * Роль пользователя.
     */
    private Role role;

    /**
     * Флаг активности пользователя.
     */
    private Boolean isActive;

    /**
     * Информация о WB API ключе (только для SELLER).
     */
    private ApiKeyInfo apiKey;

    /**
     * Информация о WB API ключе пользователя.
     */
    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ApiKeyInfo {
        /**
         * Флаг валидности ключа.
         */
        private Boolean isValid;

        /**
         * Дата последней валидации ключа.
         */
        private LocalDateTime lastValidatedAt;

        /**
         * Описание ошибки валидации, если ключ невалиден.
         */
        private String validationError;
    }
}

