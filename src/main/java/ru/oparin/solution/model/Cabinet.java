package ru.oparin.solution.model;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * Кабинет продавца. Объединяет данные кабинета и WB API ключ (один ключ на кабинет).
 * У одного пользователя (SELLER) может быть несколько кабинетов.
 */
@Entity
@Table(name = "cabinets", schema = "solution")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Cabinet {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Владелец-продавец (User с ролью SELLER).
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /**
     * Название кабинета (обязательное, задаётся при создании, можно редактировать).
     */
    @Column(name = "name", nullable = false, length = 255)
    private String name;

    /**
     * WB API ключ Wildberries. Кабинет может существовать без ключа (null).
     */
    @Column(name = "api_key", length = 500)
    private String apiKey;

    /**
     * Флаг валидности ключа (null до первой проверки).
     */
    @Column(name = "is_valid")
    private Boolean isValid;

    @Column(name = "last_validated_at")
    private LocalDateTime lastValidatedAt;

    @Column(name = "validation_error", columnDefinition = "TEXT")
    private String validationError;

    @Column(name = "last_data_update_at")
    private LocalDateTime lastDataUpdateAt;

    /**
     * Время запроса обновления (нажатие кнопки). Сбрасывается при реальном старте задачи.
     * Нужно для блокировки повторных нажатий, пока задача в очереди.
     */
    @Column(name = "last_data_update_requested_at")
    private LocalDateTime lastDataUpdateRequestedAt;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
