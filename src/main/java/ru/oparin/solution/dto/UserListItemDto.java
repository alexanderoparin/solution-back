package ru.oparin.solution.dto;

import lombok.*;
import ru.oparin.solution.model.Role;

import java.time.LocalDateTime;

/**
 * DTO для отображения пользователя в списке.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserListItemDto {

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
     * Дата создания.
     */
    private LocalDateTime createdAt;

    /**
     * Email селлера-работодателя (только для WORKER).
     */
    private String ownerEmail;

    /**
     * Дата последнего обновления данных (для селлеров).
     */
    private LocalDateTime lastDataUpdateAt;

    /**
     * Время запроса обновления (кнопка нажата, задача в очереди). Для блокировки кнопки на Сводной.
     */
    private LocalDateTime lastDataUpdateRequestedAt;
}

