package ru.oparin.solution.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.*;

import java.io.Serializable;

/**
 * Составной ключ накопителя агрегатов отзывов: запуск + nmId.
 */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class FeedbacksSyncAccumulatorId implements Serializable {

    @Column(name = "run_id", nullable = false)
    private Long runId;

    @Column(name = "nm_id", nullable = false)
    private Long nmId;
}
