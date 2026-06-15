package ru.oparin.solution.dto;

import lombok.*;
import ru.oparin.solution.model.Role;

import java.time.LocalDateTime;
import java.util.List;

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
     * Email менеджеров с активным доступом (только для SELLER).
     */
    private List<String> managerEmails;

    /**
     * Дата последнего обновления данных (для селлеров).
     */
    private LocalDateTime lastDataUpdateAt;

    /**
     * Время запроса обновления (кнопка нажата, задача в очереди). Для блокировки кнопки на Сводной.
     */
    private LocalDateTime lastDataUpdateRequestedAt;
}

