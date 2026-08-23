package ru.oparin.solution.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Ошибка обновления данных кабинета (основные данные или остатки).
 */
@Entity
@Table(name = "cabinet_update_errors", schema = "solution")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CabinetUpdateError {

    /**
     * Уникальный идентификатор записи.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Кабинет, при обновлении которого произошла ошибка.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cabinet_id", nullable = false)
    private Cabinet cabinet;

    /**
     * Область обновления: основные данные или остатки.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "scope", nullable = false, length = 20)
    private CabinetUpdateErrorScope scope;

    /**
     * Время возникновения ошибки.
     */
    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;

    /**
     * Текст ошибки.
     */
    @Column(name = "error_message", nullable = false, columnDefinition = "TEXT")
    private String errorMessage;
}
