package ru.oparin.solution.model;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Гипотеза по артикулу WB: сравнение метрик за период проверки с равным периодом до него.
 */
@Entity
@Table(name = "wb_hypothesis", schema = "solution")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WbHypothesis {

    /**
     * Идентификатор гипотезы.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Кабинет.
     */
    @Column(name = "cabinet_id", nullable = false)
    private Long cabinetId;

    /**
     * Артикул WB.
     */
    @Column(name = "nm_id", nullable = false)
    private Long nmId;

    /**
     * Название.
     */
    @Column(name = "title", nullable = false, length = 500)
    private String title;

    /**
     * Описание.
     */
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    /**
     * Начало периода проверки.
     */
    @Column(name = "check_from", nullable = false)
    private LocalDate checkFrom;

    /**
     * Конец периода проверки.
     */
    @Column(name = "check_to", nullable = false)
    private LocalDate checkTo;

    /**
     * Пользовательский статус жизненного цикла.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "workflow_status", nullable = false, length = 32)
    private WbHypothesisWorkflowStatus workflowStatus;

    /**
     * JSON-массив ключей критериев.
     */
    @Column(name = "criteria_json", nullable = false, columnDefinition = "TEXT")
    private String criteriaJson;

    /**
     * Итог проверки.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "verdict", length = 32)
    private WbHypothesisVerdict verdict;

    /**
     * Итог задан вручную.
     */
    @Column(name = "verdict_manual", nullable = false)
    @Builder.Default
    private boolean verdictManual = false;

    /**
     * Автор гипотезы.
     */
    @Column(name = "created_by_user_id")
    private Long createdByUserId;

    /**
     * Дата создания.
     */
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * Дата обновления.
     */
    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
