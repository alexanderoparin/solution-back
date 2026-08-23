package ru.oparin.solution.model;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * Редактируемая «цель на артикул» в разрезе кабинета.
 */
@Entity
@Table(
        name = "wb_article_goals",
        schema = "solution",
        uniqueConstraints = @UniqueConstraint(columnNames = {"cabinet_id", "nm_id"})
)
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WbArticleGoal {

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
     * Артикул WB (nm_id).
     */
    @Column(name = "nm_id", nullable = false)
    private Long nmId;

    /**
     * Текст цели на артикул.
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
