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
@Table(name = "wb_campaign_management_state", schema = "solution")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WbCampaignManagementState {

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
     * Кампания остановлена вручную пользователем.
     */
    @Column(name = "manual_stopped", nullable = false)
    private boolean manualStopped;

    /**
     * Управление по расписанию включено.
     */
    @Column(name = "schedule_enabled", nullable = false)
    private boolean scheduleEnabled;

    /**
     * Идентификатор текущего активного слота.
     */
    @Column(name = "active_slot_id")
    private Long activeSlotId;

    /**
     * Бюджет кампании на момент старта слота, руб.
     */
    @Column(name = "budget_at_slot_start")
    private Integer budgetAtSlotStart;

    /**
     * Последний известный общий бюджет, руб.
     */
    @Column(name = "last_budget_total")
    private Integer lastBudgetTotal;

    /**
     * Время последней проверки бюджета.
     */
    @Column(name = "last_budget_checked_at")
    private LocalDateTime lastBudgetCheckedAt;

    /**
     * Число автопополнений за текущие сутки.
     */
    @Column(name = "top_ups_today_count", nullable = false)
    private int topUpsTodayCount;

    /**
     * Дата, к которой относится счётчик пополнений.
     */
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

    /** Последняя проверка бюджета при блокировке запуска (МСК). */
    @Column(name = "start_no_budget_checked_at")
    private LocalDateTime startNoBudgetCheckedAt;

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
