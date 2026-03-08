package ru.oparin.solution.model;

import jakarta.persistence.*;
import lombok.*;
import ru.oparin.solution.service.wb.WbApiCategory;

import java.time.LocalDateTime;

/**
 * Результат последней проверки доступа к категории WB API по кабинету.
 * Обновляется после каждого блока обновлений: при успехе — success=true, при 401 — success=false.
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

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cabinet_id", nullable = false)
    private Cabinet cabinet;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 50)
    private WbApiCategory category;

    @Column(name = "last_checked_at", nullable = false)
    private LocalDateTime lastCheckedAt;

    @Column(name = "success", nullable = false)
    private Boolean success;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;
}
