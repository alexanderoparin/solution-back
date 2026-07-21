package ru.oparin.solution.model;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;

/**
 * Слот расписания запуска рекламной кампании.
 */
@Entity
@Table(name = "campaign_schedule_slot", schema = "solution")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CampaignScheduleSlot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "campaign_id", nullable = false)
    private Long campaignId;

    @Column(name = "cabinet_id", nullable = false)
    private Long cabinetId;

    /** 1 = понедельник … 7 = воскресенье. */
    @Column(name = "day_of_week", nullable = false)
    private short dayOfWeek;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    @Column(name = "budget_rub", nullable = false)
    private Integer budgetRub;

    @Column(name = "repeat_group_id")
    private UUID repeatGroupId;

    @Enumerated(EnumType.STRING)
    @Column(name = "repeat_mode", nullable = false, length = 20)
    @Builder.Default
    private CampaignSlotRepeatMode repeatMode = CampaignSlotRepeatMode.DAILY;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
