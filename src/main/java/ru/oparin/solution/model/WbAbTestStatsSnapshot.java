package ru.oparin.solution.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Снимок статистики РК по артикулу для расчёта дельт А/Б-теста.
 */
@Entity
@Table(name = "wb_ab_test_stats_snapshot", schema = "solution",
        uniqueConstraints = @UniqueConstraint(columnNames = {"ab_test_id", "advert_id", "nm_id"}))
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WbAbTestStatsSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ab_test_id", nullable = false)
    private Long abTestId;

    @Column(name = "advert_id", nullable = false)
    private Long advertId;

    @Column(name = "nm_id", nullable = false)
    private Long nmId;

    @Column(name = "views", nullable = false)
    @Builder.Default
    private int views = 0;

    @Column(name = "clicks", nullable = false)
    @Builder.Default
    private int clicks = 0;

    @Column(name = "atbs", nullable = false)
    @Builder.Default
    private int atbs = 0;

    @Column(name = "orders", nullable = false)
    @Builder.Default
    private int orders = 0;

    @Column(name = "captured_at", nullable = false)
    private LocalDateTime capturedAt;
}
