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

    /** WB API ключ / Ozon Seller Api-Key (in-memory, Phase 5.2). */
    @Transient
    private String apiKey;

    /** Ozon Seller Client-Id (in-memory). */
    @Transient
    private String ozonClientId;

    @Transient
    private String ozonPerformanceClientId;

    @Transient
    private String ozonPerformanceClientSecret;

    @Transient
    private Boolean ozonPerformanceIsValid;

    @Transient
    private LocalDateTime ozonPerformanceLastValidatedAt;

    @Transient
    private String ozonPerformanceValidationError;

    @Transient
    private LocalDateTime lastOzonCampaignsSyncAt;

    @Builder.Default
    @Transient
    private CabinetTokenType tokenType = CabinetTokenType.BASIC;

    @Transient
    private Boolean isValid;

    @Transient
    private LocalDateTime lastValidatedAt;

    @Transient
    private String validationError;

    @Transient
    private LocalDateTime lastDataUpdateAt;

    @Transient
    private LocalDateTime lastDataUpdateRequestedAt;

    @Transient
    private LocalDateTime lastStocksUpdateRequestedAt;

    @Transient
    private LocalDateTime lastStocksUpdateAt;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
