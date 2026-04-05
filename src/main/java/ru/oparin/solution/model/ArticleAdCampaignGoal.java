package ru.oparin.solution.model;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * Редактируемая «цель рекламной кампании» для артикула в кабинете.
 */
@Entity
@Table(
        name = "article_ad_campaign_goals",
        schema = "solution",
        uniqueConstraints = @UniqueConstraint(columnNames = {"cabinet_id", "nm_id"})
)
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArticleAdCampaignGoal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cabinet_id", nullable = false)
    private Long cabinetId;

    @Column(name = "nm_id", nullable = false)
    private Long nmId;

    @Column(name = "goal_text", nullable = false, columnDefinition = "TEXT")
    @Builder.Default
    private String goalText = "";

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
