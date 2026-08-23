package ru.oparin.solution.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Запись временной шкалы бюджета рекламной кампании.
 */
@Entity
@Table(name = "wb_campaign_budget_timeline", schema = "solution")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WbCampaignBudgetTimeline {

    /**
     * Уникальный идентификатор записи.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Идентификатор рекламной кампании.
     */
    @Column(name = "campaign_id", nullable = false)
    private Long campaignId;

    /**
     * Идентификатор кабинета WB.
     */
    @Column(name = "cabinet_id", nullable = false)
    private Long cabinetId;

    /**
     * Время фиксации события.
     */
    @Column(name = "recorded_at", nullable = false)
    private LocalDateTime recordedAt;

    /**
     * Тип события на шкале бюджета.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 16)
    private WbCampaignBudgetTimelineEventType eventType;

    /**
     * Общий бюджет кампании после события, руб.
     */
    @Column(name = "budget_total")
    private Integer budgetTotal;

    /**
     * Сумма пополнения, руб.
     */
    @Column(name = "top_up_amount")
    private Integer topUpAmount;
}
