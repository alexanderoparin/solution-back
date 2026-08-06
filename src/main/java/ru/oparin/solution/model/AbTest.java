package ru.oparin.solution.model;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * А/Б-тест главного фото карточки Wildberries.
 */
@Entity
@Table(name = "ab_test", schema = "solution")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AbTest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cabinet_id", nullable = false)
    private Long cabinetId;

    @Column(name = "nm_id", nullable = false)
    private Long nmId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private AbTestStatus status = AbTestStatus.ENABLED;

    @Enumerated(EnumType.STRING)
    @Column(name = "rotation_mode", nullable = false, length = 32)
    private AbTestRotationMode rotationMode;

    @Column(name = "rotation_views_threshold")
    private Integer rotationViewsThreshold;

    @Column(name = "rotation_interval_minutes")
    private Integer rotationIntervalMinutes;

    @Enumerated(EnumType.STRING)
    @Column(name = "stop_mode", nullable = false, length = 32)
    private AbTestStopMode stopMode;

    @Column(name = "duration_days")
    private Integer durationDays;

    @Column(name = "ends_at")
    private LocalDateTime endsAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "finish_action", nullable = false, length = 32)
    private AbTestFinishAction finishAction;

    @Column(name = "original_main_photo_url", length = 1000)
    private String originalMainPhotoUrl;

    @Column(name = "original_gallery_urls_json", columnDefinition = "TEXT")
    private String originalGalleryUrlsJson;

    @Column(name = "active_variant_id")
    private Long activeVariantId;

    /** Views активного варианта на момент, когда он стал активным (якорь для ротации по показам). */
    @Column(name = "active_since_views", nullable = false)
    @Builder.Default
    private long activeSinceViews = 0;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    @Column(name = "last_rotated_at")
    private LocalDateTime lastRotatedAt;

    @Column(name = "last_stats_at")
    private LocalDateTime lastStatsAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "insight_code", length = 32)
    private AbTestInsightCode insightCode;

    /** Последняя ошибка асинхронного вызова WB (старт / смена фото / статистика). */
    @Column(name = "last_wb_error", columnDefinition = "TEXT")
    private String lastWbError;

    /**
     * Старт не завершился успешно (ошибка / отмена из PENDING_START).
     * Такие тесты можно перезапустить после исправления токена.
     */
    @Builder.Default
    @Column(name = "failed_at_start", nullable = false)
    private Boolean failedAtStart = false;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
