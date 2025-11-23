package ru.oparin.solution.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * Сущность WB API ключа пользователя.
 */
@Entity
@Table(name = "wb_api_keys", schema = "solution")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WbApiKey {

    /**
     * Уникальный идентификатор ключа.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Пользователь, которому принадлежит ключ.
     */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    /**
     * Сам WB API ключ.
     */
    @Column(name = "api_key", nullable = false, length = 500)
    private String apiKey;

    /**
     * Флаг валидности ключа (null до первой проверки).
     */
    @Column(name = "is_valid")
    private Boolean isValid;

    /**
     * Дата последней валидации ключа.
     */
    @Column(name = "last_validated_at")
    private LocalDateTime lastValidatedAt;

    /**
     * Описание ошибки валидации, если ключ невалиден.
     */
    @Column(name = "validation_error", columnDefinition = "TEXT")
    private String validationError;

    /**
     * Дата создания записи.
     */
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * Дата последнего обновления записи.
     */
    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}

