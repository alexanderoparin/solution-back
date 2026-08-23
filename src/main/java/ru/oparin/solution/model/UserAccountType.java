package ru.oparin.solution.model;

import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Связь пользователя с номинальным типом аккаунта (статистика и отображение).
 */
@Entity
@Table(name = "user_account_types", schema = "solution")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@IdClass(UserAccountType.UserAccountTypeId.class)
public class UserAccountType {

    /**
     * Идентификатор пользователя (часть составного ключа).
     */
    @Id
    @Column(name = "user_id")
    private Long userId;

    /**
     * Тип аккаунта (часть составного ключа).
     */
    @Id
    @Enumerated(EnumType.STRING)
    @Column(name = "account_type", length = 20)
    private AccountType accountType;

    /**
     * Пользователь.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", insertable = false, updatable = false)
    private User user;

    /**
     * Дата создания записи.
     */
    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    /**
     * Составной первичный ключ (userId + accountType).
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    public static class UserAccountTypeId implements Serializable {

        /**
         * Идентификатор пользователя.
         */
        private Long userId;

        /**
         * Тип аккаунта.
         */
        private AccountType accountType;
    }
}
