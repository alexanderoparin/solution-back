package ru.oparin.solution.model;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Рекламная кампания Ozon Performance API.
 * Источник: GET {@code /api/client/campaign}.
 */
@Entity
@Table(name = "ozon_promotion_campaigns", schema = "solution")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OzonPromotionCampaign {

    /**
     * ID кампании в Ozon Performance API.
     */
    @Id
    @Column(name = "campaign_id")
    private Long campaignId;

    /**
     * Кабинет, которому принадлежит кампания.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cabinet_id", nullable = false)
    private Cabinet cabinet;

    /**
     * Название кампании.
     */
    @Column(name = "title", nullable = false, length = 500)
    private String title;

    /**
     * Статус кампании в Ozon (RUNNING, STOPPED и т.д.).
     */
    @Column(name = "state", nullable = false, length = 64)
    private String state;

    /**
     * Тип рекламируемого объекта (SKU, BRAND и т.д.).
     */
    @Column(name = "adv_object_type", length = 32)
    private String advObjectType;

    /**
     * Модель оплаты (CPC, CPM).
     */
    @Column(name = "payment_type", length = 16)
    private String paymentType;

    /**
     * Дневной бюджет кампании (копейки).
     */
    @Column(name = "daily_budget")
    private Long dailyBudget;

    /**
     * Общий бюджет кампании (копейки).
     */
    @Column(name = "budget")
    private Long budget;

    /**
     * Дата начала кампании.
     */
    @Column(name = "from_date")
    private LocalDate fromDate;

    /**
     * Дата окончания кампании.
     */
    @Column(name = "to_date")
    private LocalDate toDate;

    /**
     * Дата создания кампании в Ozon.
     */
    @Column(name = "ozon_created_at")
    private LocalDateTime ozonCreatedAt;

    /**
     * Дата последнего изменения кампании в Ozon.
     */
    @Column(name = "ozon_updated_at")
    private LocalDateTime ozonUpdatedAt;

    /**
     * Время последней синхронизации из API.
     */
    @Column(name = "synced_at", nullable = false)
    private LocalDateTime syncedAt;

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
