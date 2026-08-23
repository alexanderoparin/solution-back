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
@Table(name = "wb_ab_test", schema = "solution")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WbAbTest {

    /**
     * Уникальный идентификатор теста.
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
     * Статус теста.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private WbAbTestStatus status = WbAbTestStatus.ENABLED;

    /**
     * Режим ротации вариантов.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "rotation_mode", nullable = false, length = 32)
    private WbAbTestRotationMode rotationMode;

    /**
     * Порог показов для ротации (режим по показам).
     */
    @Column(name = "rotation_views_threshold")
    private Integer rotationViewsThreshold;

    /**
     * Интервал ротации в минутах (режим по времени).
     */
    @Column(name = "rotation_interval_minutes")
    private Integer rotationIntervalMinutes;

    /**
     * Условие остановки теста.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "stop_mode", nullable = false, length = 32)
    private WbAbTestStopMode stopMode;

    /**
     * Длительность теста в днях.
     */
    @Column(name = "duration_days")
    private Integer durationDays;

    /**
     * Плановое время завершения теста.
     */
    @Column(name = "ends_at")
    private LocalDateTime endsAt;

    /**
     * Действие после завершения теста.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "finish_action", nullable = false, length = 32)
    private WbAbTestFinishAction finishAction;

    /**
     * URL исходного главного фото до старта теста.
     */
    @Column(name = "original_main_photo_url", length = 1000)
    private String originalMainPhotoUrl;

    /**
     * JSON с URL исходной галереи до старта теста.
     */
    @Column(name = "original_gallery_urls_json", columnDefinition = "TEXT")
    private String originalGalleryUrlsJson;

    /**
     * Идентификатор текущего активного варианта.
     */
    @Column(name = "active_variant_id")
    private Long activeVariantId;

    /**
     * Views активного варианта на момент, когда он стал активным (якорь для ротации по показам).
     */
    @Column(name = "active_since_views", nullable = false)
    @Builder.Default
    private long activeSinceViews = 0;

    /**
     * Фактическое время старта теста.
     */
    @Column(name = "started_at")
    private LocalDateTime startedAt;

    /**
     * Время завершения теста.
     */
    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    /**
     * Время последней ротации варианта.
     */
    @Column(name = "last_rotated_at")
    private LocalDateTime lastRotatedAt;

    /**
     * Время последнего обновления статистики.
     */
    @Column(name = "last_stats_at")
    private LocalDateTime lastStatsAt;

    /**
     * Код инсайта по результатам теста.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "insight_code", length = 32)
    private WbAbTestInsightCode insightCode;

    /**
     * Последняя ошибка асинхронного вызова WB (старт / смена фото / статистика).
     */
    @Column(name = "last_wb_error", columnDefinition = "TEXT")
    private String lastWbError;

    /**
     * Старт не завершился успешно (ошибка / отмена из PENDING_START).
     * Такие тесты можно перезапустить после исправления токена.
     */
    @Builder.Default
    @Column(name = "failed_at_start", nullable = false)
    private Boolean failedAtStart = false;

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
