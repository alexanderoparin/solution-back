package ru.oparin.solution.model;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * Метки синхронизации кабинета.
 * Phase 5.2: каноническое хранение в {@code cabinet_sync_state}; поля {@link Cabinet} — {@code @Transient} overlay.
 */
@Entity
@Table(name = "cabinet_sync_state", schema = "solution")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CabinetSyncState {

    /**
     * Идентификатор кабинета (PK, FK на cabinets).
     */
    @Id
    @Column(name = "cabinet_id")
    private Long cabinetId;

    /**
     * Время последнего успешного обновления основных данных.
     */
    @Column(name = "last_data_update_at")
    private LocalDateTime lastDataUpdateAt;

    /**
     * Время последнего запроса на обновление основных данных.
     */
    @Column(name = "last_data_update_requested_at")
    private LocalDateTime lastDataUpdateRequestedAt;

    /**
     * Время последнего успешного обновления остатков.
     */
    @Column(name = "last_stocks_update_at")
    private LocalDateTime lastStocksUpdateAt;

    /**
     * Время последнего запроса на обновление остатков.
     */
    @Column(name = "last_stocks_update_requested_at")
    private LocalDateTime lastStocksUpdateRequestedAt;

    /**
     * Время последней синхронизации рекламных кампаний Ozon.
     */
    @Column(name = "last_ozon_campaigns_sync_at")
    private LocalDateTime lastOzonCampaignsSyncAt;

    /**
     * Тип подписки Ozon Seller ({@link OzonSellerSubscriptionType}).
     */
    @Column(name = "ozon_subscription_type", length = 32)
    private String ozonSubscriptionType;

    /**
     * Флаг {@code is_premium} из seller/info.
     */
    @Column(name = "ozon_subscription_is_premium")
    private Boolean ozonSubscriptionIsPremium;

    /**
     * Расширенная воронка доступна в analytics/data (probe).
     */
    @Column(name = "ozon_analytics_funnel_available")
    private Boolean ozonAnalyticsFunnelAvailable;

    /**
     * Когда последний раз обновляли данные о подписке Ozon.
     */
    @Column(name = "ozon_subscription_checked_at")
    private LocalDateTime ozonSubscriptionCheckedAt;

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
