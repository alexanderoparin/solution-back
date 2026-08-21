package ru.oparin.solution.model;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * Кабинет продавца на одном маркетплейсе ({@link MarketplaceType}).
 * Для WB хранит API-ключ; для Ozon — отдельные credentials (добавятся позже).
 * У одного пользователя может быть несколько кабинетов (в т.ч. с одинаковым именем на разных МП).
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
     * Маркетплейс кабинета. Задаётся при создании, не меняется.
     */
    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "marketplace_type", nullable = false, length = 16, updatable = false)
    private MarketplaceType marketplaceType = MarketplaceType.WB;

    /**
     * Название кабинета (обязательное, задаётся при создании, можно редактировать).
     */
    @Column(name = "name", nullable = false, length = 255)
    private String name;

    /**
     * WB API ключ Wildberries. Кабинет может существовать без ключа (null).
     * Для Ozon-кабинетов не используется (отдельные поля credentials — позже).
     */
    @Column(name = "api_key", length = 500)
    private String apiKey;

    /**
     * Тип WB API токена кабинета.
     */
    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "token_type", nullable = false, length = 32)
    private CabinetTokenType tokenType = CabinetTokenType.BASIC;

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

    /**
     * Время последнего запуска обновления только остатков по кабинету (кнопка «Обновить остатки»).
     * Используется для ограничения «не чаще раза в час» и для отображения на фронте.
     */
    @Column(name = "last_stocks_update_requested_at")
    private LocalDateTime lastStocksUpdateRequestedAt;

    /**
     * Время последнего успешного завершения обновления остатков по кабинету.
     */
    @Column(name = "last_stocks_update_at")
    private LocalDateTime lastStocksUpdateAt;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
