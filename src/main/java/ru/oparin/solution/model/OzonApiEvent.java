package ru.oparin.solution.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Событие очереди Ozon Seller API.
 */
@Entity
@Table(name = "ozon_api_events", schema = "solution")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OzonApiEvent {

    /**
     * Уникальный идентификатор события.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Тип операции Ozon Seller API.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 80)
    private OzonApiEventType eventType;

    /**
     * Статус обработки в очереди.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 40)
    private OzonApiEventStatus status;

    /**
     * Имя Spring-bean исполнителя события.
     */
    @Column(name = "executor_bean_name", nullable = false, length = 120)
    private String executorBeanName;

    /**
     * Кабинет, для которого выполняется событие.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cabinet_id", nullable = false)
    private Cabinet cabinet;

    /**
     * JSON-параметры события.
     */
    @Column(name = "payload_json", columnDefinition = "TEXT")
    private String payloadJson;

    /**
     * Ключ дедупликации активных задач.
     */
    @Column(name = "dedup_key", nullable = false, length = 255)
    private String dedupKey;

    /**
     * Текущее число попыток выполнения.
     */
    @Column(name = "attempt_count", nullable = false)
    private Integer attemptCount;

    /**
     * Максимальное число попыток выполнения.
     */
    @Column(name = "max_attempts", nullable = false)
    private Integer maxAttempts;

    /**
     * Время следующей попытки выполнения.
     */
    @Column(name = "next_attempt_at", nullable = false)
    private LocalDateTime nextAttemptAt;

    /**
     * Текст последней ошибки.
     */
    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    /**
     * Приоритет в очереди (выше — раньше).
     */
    @Column(name = "priority", nullable = false)
    private Integer priority;

    /**
     * Источник постановки события в очередь.
     */
    @Column(name = "trigger_source", nullable = false, length = 40)
    private String triggerSource;

    /**
     * Дата создания записи.
     */
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    /**
     * Время начала выполнения.
     */
    @Column(name = "started_at")
    private LocalDateTime startedAt;

    /**
     * Время завершения выполнения.
     */
    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    /**
     * Дата последнего обновления записи.
     */
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
