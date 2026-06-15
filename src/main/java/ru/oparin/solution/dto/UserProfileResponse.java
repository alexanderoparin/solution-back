package ru.oparin.solution.dto;

import lombok.*;
import ru.oparin.solution.model.CabinetTokenType;
import ru.oparin.solution.model.Role;

import java.time.Instant;
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
     * Почта подтверждена.
     */
    private Boolean emailConfirmed;

    /**
     * Дата и время последней отправки письма для подтверждения почты (повтор не чаще 1 раза в 24 ч).
     */
    private Instant lastEmailConfirmationSentAt;

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
         * Тип WB API токена.
         */
        private CabinetTokenType tokenType;

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

