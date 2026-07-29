package ru.oparin.solution.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Запись о смене активного варианта фото в А/Б-тесте.
 */
@Entity
@Table(name = "ab_test_rotation_log", schema = "solution")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AbTestRotationLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ab_test_id", nullable = false)
    private Long abTestId;

    @Column(name = "variant_id", nullable = false)
    private Long variantId;

    @Column(name = "switched_at", nullable = false)
    private LocalDateTime switchedAt;

    @Column(name = "reason", length = 64)
    private String reason;
}
