package ru.oparin.solution.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Запись временной шкалы бюджета рекламной кампании.
 */
@Entity
@Table(name = "campaign_budget_timeline", schema = "solution")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CampaignBudgetTimeline {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "campaign_id", nullable = false)
    private Long campaignId;

    @Column(name = "cabinet_id", nullable = false)
    private Long cabinetId;

    @Column(name = "recorded_at", nullable = false)
    private LocalDateTime recordedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 16)
    private CampaignBudgetTimelineEventType eventType;

    @Column(name = "budget_total")
    private Integer budgetTotal;

    @Column(name = "top_up_amount")
    private Integer topUpAmount;
}
