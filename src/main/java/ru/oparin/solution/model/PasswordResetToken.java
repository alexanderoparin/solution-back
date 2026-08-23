package ru.oparin.solution.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * Токен для сброса пароля по ссылке из письма.
 */
@Entity
@Table(name = "password_reset_tokens", schema = "solution")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PasswordResetToken {

    /**
     * Уникальный идентификатор токена.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Пользователь, сбрасывающий пароль.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /**
     * Одноразовый токен из ссылки письма.
     */
    @Column(nullable = false, unique = true, length = 255)
    private String token;

    /**
     * Срок действия токена.
     */
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    /**
     * Дата создания токена.
     */
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
