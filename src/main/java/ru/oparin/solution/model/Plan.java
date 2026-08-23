package ru.oparin.solution.model;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * Тарифный план подписки.
 */
@Entity
@Table(name = "plans", schema = "solution")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Plan {

    /**
     * Уникальный идентификатор тарифа.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Название тарифа.
     */
    @Column(name = "name", nullable = false, length = 255)
    private String name;

    /**
     * Описание тарифа.
     */
    @Column(name = "description")
    private String description;

    /**
     * Цена в рублях.
     */
    @Column(name = "price_rub", nullable = false, precision = 10, scale = 2)
    private java.math.BigDecimal priceRub;

    /**
     * Длительность периода в днях.
     */
    @Column(name = "period_days", nullable = false)
    private Integer periodDays;

    /**
     * Код тарифа.
     */
    @Column(name = "code", length = 50)
    private String code;

    /**
     * Категория тарифа (основной, управление РК, пакет А/Б).
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "kind", length = 20)
    private PlanKind kind;

    /**
     * Число кредитов А/Б для пакетов {@link PlanKind#AB_PACK}.
     */
    @Column(name = "credit_amount")
    private Integer creditAmount;

    /**
     * Способ расчёта срока подписки.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "period_type", nullable = false, length = 20)
    @Builder.Default
    private PlanPeriodType periodType = PlanPeriodType.DAYS;

    /**
     * Максимальное число кабинетов.
     */
    @Column(name = "max_cabinets")
    private Integer maxCabinets;

    /**
     * Порядок сортировки при отображении.
     */
    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;

    /**
     * Флаг активности тарифа.
     */
    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

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
