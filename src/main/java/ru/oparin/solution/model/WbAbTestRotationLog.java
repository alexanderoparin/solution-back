package ru.oparin.solution.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Запись о смене активного варианта фото в А/Б-тесте.
 */
@Entity
@Table(name = "wb_ab_test_rotation_log", schema = "solution")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WbAbTestRotationLog {

    /**
     * Уникальный идентификатор записи.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Идентификатор А/Б-теста.
     */
    @Column(name = "ab_test_id", nullable = false)
    private Long abTestId;

    /**
     * Идентификатор активированного варианта.
     */
    @Column(name = "variant_id", nullable = false)
    private Long variantId;

    /**
     * Момент переключения варианта.
     */
    @Column(name = "switched_at", nullable = false)
    private LocalDateTime switchedAt;

    /**
     * Причина ротации (код или описание).
     */
    @Column(name = "reason", length = 64)
    private String reason;
}
