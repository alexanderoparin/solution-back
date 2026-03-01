package ru.oparin.solution.dto;

import lombok.*;
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
     * Почта подтверждена (актуально для сторонних селлеров; у клиентов агентства не используется).
     */
    private Boolean emailConfirmed;

    /**
     * Селлер является клиентом агентства (создан менеджером, привязан к owner).
     */
    private Boolean isAgencyClient;

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
         * WB API ключ.
         */
        private String apiKey;

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

        /**
         * Дата последнего запуска обновления данных.
         */
        private LocalDateTime lastDataUpdateAt;
    }
}

