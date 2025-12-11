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
 * Связь 1:1 с User - у каждого SELLER'а только один WB API ключ со всеми правами.
 * Через этот ключ ночью подтягиваются данные из Wildberries API.
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
     * ID пользователя (SELLER) - первичный ключ таблицы.
     * Связь 1:1 - у каждого пользователя только один WB API ключ со всеми правами.
     */
    @Id
    @Column(name = "user_id")
    private Long userId;

    /**
     * Пользователь (SELLER), которому принадлежит ключ.
     * Связь 1:1 - у каждого пользователя только один WB API ключ со всеми правами.
     */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, insertable = false, updatable = false)
    private User user;

    /**
     * WB API ключ Wildberries со всеми правами доступа.
     * Используется для ночной загрузки данных из WB API.
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
     * Дата последнего запуска обновления данных.
     */
    @Column(name = "last_data_update_at")
    private LocalDateTime lastDataUpdateAt;

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

