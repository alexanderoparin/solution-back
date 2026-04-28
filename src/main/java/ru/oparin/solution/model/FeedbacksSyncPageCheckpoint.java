package ru.oparin.solution.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Чекпоинт применённой страницы отзывов (run + phase + skip) для идемпотентной пагинации.
 */
@Entity
@Table(
        name = "feedbacks_sync_page_checkpoint",
        schema = "solution",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_feedbacks_sync_page",
                columnNames = {"run_id", "is_answered", "skip_value"}
        )
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FeedbacksSyncPageCheckpoint {

    /** Уникальный идентификатор чекпоинта страницы. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Идентификатор run, к которому относится страница. */
    @Column(name = "run_id", nullable = false)
    private Long runId;

    /** Фаза: true — обработанные, false — необработанные отзывы. */
    @Column(name = "is_answered", nullable = false)
    private Boolean isAnswered;

    /** Значение skip для страницы, уже применённой в агрегатор. */
    @Column(name = "skip_value", nullable = false)
    private Integer skipValue;

    /** Время создания чекпоинта. */
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
