package ru.oparin.solution.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Один запуск пошаговой синхронизации отзывов для кабинета.
 * Хранит общий прогресс и финальный статус run.
 */
@Entity
@Table(name = "feedbacks_sync_runs", schema = "solution")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FeedbacksSyncRun {

    /** Уникальный идентификатор запуска синхронизации отзывов. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Кабинет, для которого выполняется run. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cabinet_id", nullable = false)
    private Cabinet cabinet;

    /** Текущий статус запуска: RUNNING / COMPLETED / FAILED. */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private FeedbacksSyncRunStatus status;

    /** Источник запуска (scheduler/manual/admin bulk и т.п.). */
    @Column(name = "trigger_source", nullable = false, length = 40)
    private String triggerSource;

    /** Тип токена на старте run (нужен для консистентной задержки между шагами). */
    @Enumerated(EnumType.STRING)
    @Column(name = "token_type_snapshot", nullable = false, length = 20)
    private CabinetTokenType tokenTypeSnapshot;

    /** Последняя ошибка запуска (если статус FAILED). */
    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    /** Время создания run. */
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    /** Время последнего изменения run. */
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /** Время завершения run (при COMPLETED/FAILED). */
    @Column(name = "finished_at")
    private LocalDateTime finishedAt;
}
