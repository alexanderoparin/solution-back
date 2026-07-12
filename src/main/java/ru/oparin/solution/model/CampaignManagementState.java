package ru.oparin.solution.model;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Операционное состояние управления рекламной кампанией.
 */
@Entity
@Table(name = "campaign_management_state", schema = "solution")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CampaignManagementState {

    @Id
    @Column(name = "campaign_id", nullable = false)
    private Long campaignId;

    @Column(name = "cabinet_id", nullable = false)
    private Long cabinetId;

    @Column(name = "manual_stopped", nullable = false)
    private boolean manualStopped;

    @Column(name = "schedule_enabled", nullable = false)
    private boolean scheduleEnabled;

    @Column(name = "active_slot_id")
    private Long activeSlotId;

    @Column(name = "budget_at_slot_start")
    private Integer budgetAtSlotStart;

    @Column(name = "last_budget_total")
    private Integer lastBudgetTotal;

    @Column(name = "last_budget_checked_at")
    private LocalDateTime lastBudgetCheckedAt;

    @Column(name = "top_ups_today_count", nullable = false)
    private int topUpsTodayCount;

    @Column(name = "top_ups_today_date")
    private LocalDate topUpsTodayDate;

    /** Слот, для которого исчерпан лимит бюджета; до конца окна слота РК не запускается. */
    @Column(name = "slot_budget_exhausted_slot_id")
    private Long slotBudgetExhaustedSlotId;

    /** Сумма автопополнений за текущий активный слот, руб. */
    @Column(name = "slot_top_ups_rub", nullable = false)
    @Builder.Default
    private int slotTopUpsRub = 0;

    /**
     * До этого времени (МСК) продолжаем опрашивать бюджет WB после паузы, вне активного слота.
     */
    @Column(name = "budget_trail_until")
    private LocalDateTime budgetTrailUntil;

    /**
     * Запуск по расписанию заблокирован из-за нулевого бюджета на WB (до пополнения).
     */
    @Column(name = "start_blocked_no_budget", nullable = false)
    @Builder.Default
    private boolean startBlockedNoBudget = false;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
