package ru.oparin.solution.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Результат последней проверки доступа к категории API по кабинету (WB или Ozon).
 * {@code category} — код категории ({@code CONTENT}, {@code SELLER} и т.п.).
 * Обновляется после блоков обновлений: при успехе — success=true, при отказе — success=false.
 */
@Entity
@Table(name = "cabinet_scope_status", schema = "solution",
       uniqueConstraints = @UniqueConstraint(name = "uq_cabinet_scope_cabinet_category", columnNames = {"cabinet_id", "category"}))
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CabinetScopeStatus {

    /**
     * Уникальный идентификатор записи.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Кабинет, для которого проверялась категория API.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cabinet_id", nullable = false)
    private Cabinet cabinet;

    /**
     * Код категории API (имя enum WB или Ozon, например {@code CONTENT}, {@code SELLER}).
     */
    @Column(name = "category", nullable = false, length = 50)
    private String category;

    /**
     * Время последней проверки доступа к категории.
     */
    @Column(name = "last_checked_at", nullable = false)
    private LocalDateTime lastCheckedAt;

    /**
     * Результат проверки: {@code true} — доступ есть, {@code false} — отказ (например 401).
     */
    @Column(name = "success", nullable = false)
    private Boolean success;

    /**
     * Текст ошибки последней неуспешной проверки (если был).
     */
    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    /**
     * До этого времени запрещены операции записи по категории (start/pause РК при read-only токене WB).
     */
    @Column(name = "write_blocked_until")
    private LocalDateTime writeBlockedUntil;
}
