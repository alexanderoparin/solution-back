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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cabinet_id", nullable = false)
    private Cabinet cabinet;

    @Column(name = "title", nullable = false, length = 500)
    private String title;

    @Column(name = "state", nullable = false, length = 64)
    private String state;

    @Column(name = "adv_object_type", length = 32)
    private String advObjectType;

    @Column(name = "payment_type", length = 16)
    private String paymentType;

    @Column(name = "daily_budget")
    private Long dailyBudget;

    @Column(name = "budget")
    private Long budget;

    @Column(name = "from_date")
    private LocalDate fromDate;

    @Column(name = "to_date")
    private LocalDate toDate;

    @Column(name = "ozon_created_at")
    private LocalDateTime ozonCreatedAt;

    @Column(name = "ozon_updated_at")
    private LocalDateTime ozonUpdatedAt;

    @Column(name = "synced_at", nullable = false)
    private LocalDateTime syncedAt;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
