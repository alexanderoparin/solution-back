package ru.oparin.solution.model;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * Настройки автопополнения бюджета рекламной кампании.
 */
@Entity
@Table(name = "campaign_auto_budget_settings", schema = "solution")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CampaignAutoBudgetSettings {

    @Id
    @Column(name = "campaign_id", nullable = false)
    private Long campaignId;

    @Column(name = "cabinet_id", nullable = false)
    private Long cabinetId;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @Column(name = "top_up_amount")
    private Integer topUpAmount;

    /** Источник WB: 0 счёт, 1 баланс, 3 бонусы. */
    @Column(name = "source_type")
    private Integer sourceType;

    @Column(name = "threshold_rub")
    private Integer thresholdRub;

    @Column(name = "max_top_ups_per_day")
    private Integer maxTopUpsPerDay;

    @Column(name = "locked", nullable = false)
    private boolean locked;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
