package ru.oparin.solution.model;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * Редактируемая «цель на рекламную кампанию» в разрезе кабинета.
 */
@Entity
@Table(
        name = "wb_campaign_goals",
        schema = "solution",
        uniqueConstraints = @UniqueConstraint(columnNames = {"cabinet_id", "campaign_id"})
)
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WbCampaignGoal {

    /**
     * Уникальный идентификатор записи.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Идентификатор кабинета WB.
     */
    @Column(name = "cabinet_id", nullable = false)
    private Long cabinetId;

    /**
     * Идентификатор рекламной кампании.
     */
    @Column(name = "campaign_id", nullable = false)
    private Long campaignId;

    /**
     * Текст цели на кампанию.
     */
    @Column(name = "goal_text", nullable = false, columnDefinition = "TEXT")
    @Builder.Default
    private String goalText = "";

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
