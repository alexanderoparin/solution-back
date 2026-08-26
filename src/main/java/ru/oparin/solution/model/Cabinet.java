package ru.oparin.solution.model;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * Кабинет продавца на одном маркетплейсе ({@link MarketplaceType}).
 * Phase 5.2: в БД только {@code user_id}, {@code marketplace_type}, {@code name}, audit;
 * credentials и метки синка — {@link CabinetIntegration} / {@link CabinetSyncState},
 * на entity подгружаются in-memory через {@code CabinetIntegrationMirrorService}.
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

    /**
     * Уникальный идентификатор кабинета.
     */
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
     * Метки синка (read-only join для сортировки в админке).
     */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id", referencedColumnName = "cabinet_id", insertable = false, updatable = false)
    private CabinetSyncState syncState;

    /**
     * WB API ключ / Ozon Seller Api-Key (in-memory, Phase 5.2).
     */
    @Transient
    private String apiKey;

    /**
     * Ozon Seller Client-Id (in-memory).
     */
    @Transient
    private String ozonClientId;

    /**
     * Ozon Performance Client-Id (in-memory).
     */
    @Transient
    private String ozonPerformanceClientId;

    /**
     * Ozon Performance Client-Secret (in-memory).
     */
    @Transient
    private String ozonPerformanceClientSecret;

    /**
     * Результат последней валидации Ozon Performance (in-memory).
     */
    @Transient
    private Boolean ozonPerformanceIsValid;

    /**
     * Время последней проверки Ozon Performance (in-memory).
     */
    @Transient
    private LocalDateTime ozonPerformanceLastValidatedAt;

    /**
     * Текст ошибки последней валидации Ozon Performance (in-memory).
     */
    @Transient
    private String ozonPerformanceValidationError;

    /**
     * Время последней синхронизации рекламных кампаний Ozon (in-memory).
     */
    @Transient
    private LocalDateTime lastOzonCampaignsSyncAt;

    /** Тип подписки Ozon Seller (in-memory). */
    @Transient
    private OzonSellerSubscriptionType ozonSubscriptionType;

    /** Флаг is_premium из seller/info (in-memory). */
    @Transient
    private Boolean ozonSubscriptionIsPremium;

    /** Доступна ли воронка в analytics/data (in-memory). */
    @Transient
    private Boolean ozonAnalyticsFunnelAvailable;

    /** Когда проверяли подписку Ozon (in-memory). */
    @Transient
    private LocalDateTime ozonSubscriptionCheckedAt;

    /**
     * Тип WB API-токена (in-memory).
     */
    @Builder.Default
    @Transient
    private CabinetTokenType tokenType = CabinetTokenType.BASIC;

    /**
     * Результат последней валидации основных credentials (in-memory).
     */
    @Transient
    private Boolean isValid;

    /**
     * Время последней проверки основных credentials (in-memory).
     */
    @Transient
    private LocalDateTime lastValidatedAt;

    /**
     * Текст ошибки последней валидации основных credentials (in-memory).
     */
    @Transient
    private String validationError;

    /**
     * Время последнего успешного обновления основных данных (in-memory).
     */
    @Transient
    private LocalDateTime lastDataUpdateAt;

    /**
     * Время последнего запроса на обновление основных данных (in-memory).
     */
    @Transient
    private LocalDateTime lastDataUpdateRequestedAt;

    /**
     * Время последнего запроса на обновление остатков (in-memory).
     */
    @Transient
    private LocalDateTime lastStocksUpdateRequestedAt;

    /**
     * Время последнего успешного обновления остатков (in-memory).
     */
    @Transient
    private LocalDateTime lastStocksUpdateAt;

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
