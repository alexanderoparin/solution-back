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
@Table(name = "wb_campaign_auto_budget_settings", schema = "solution")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WbCampaignAutoBudgetSettings {

    /**
     * Идентификатор рекламной кампании (PK).
     */
    @Id
    @Column(name = "campaign_id", nullable = false)
    private Long campaignId;

    /**
     * Идентификатор кабинета WB.
     */
    @Column(name = "cabinet_id", nullable = false)
    private Long cabinetId;

    /**
     * Автопополнение включено.
     */
    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    /**
     * Сумма одного пополнения, руб.
     */
    @Column(name = "top_up_amount")
    private Integer topUpAmount;

    /**
     * Источник WB: 0 счёт, 1 баланс, 3 бонусы.
     */
    @Column(name = "source_type")
    private Integer sourceType;

    /**
     * Подставлять промо-бонусы ({@code cashback_sum}/{@code cashback_percent}) при deposit
     * для источников счёт/баланс.
     */
    @Column(name = "use_promo_cashback", nullable = false)
    @Builder.Default
    private boolean usePromoCashback = true;

    /**
     * Порог бюджета для пополнения, руб.
     */
    @Column(name = "threshold_rub")
    private Integer thresholdRub;

    /**
     * Максимум пополнений в сутки.
     */
    @Column(name = "max_top_ups_per_day")
    private Integer maxTopUpsPerDay;

    /**
     * Настройки заблокированы от редактирования.
     */
    @Column(name = "locked", nullable = false)
    private boolean locked;

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
