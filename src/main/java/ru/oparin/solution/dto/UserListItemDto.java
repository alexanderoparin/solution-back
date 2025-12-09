package ru.oparin.solution.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
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
     * Флаг временного пароля.
     */
    private Boolean isTemporaryPassword;

    /**
     * Дата создания.
     */
    private LocalDateTime createdAt;

    /**
     * Email владельца (если есть).
     */
    private String ownerEmail;
}

