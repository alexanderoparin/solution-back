package ru.oparin.solution.model;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.*;

/**
 * Промежуточный агрегат по nmId в рамках одного запуска синка отзывов.
 */
@Entity
@Table(name = "feedbacks_sync_accumulator", schema = "solution")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FeedbacksSyncAccumulator {

    /** Составной ключ (runId + nmId). */
    @EmbeddedId
    private FeedbacksSyncAccumulatorId id;

    /** Сумма оценок отзывов по nmId в рамках run. */
    @Column(name = "valuation_sum", nullable = false)
    private Long valuationSum;

    /** Количество отзывов по nmId в рамках run. */
    @Column(name = "reviews_count", nullable = false)
    private Long reviewsCount;
}
