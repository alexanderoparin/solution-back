package ru.oparin.solution.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Платёж за подписку.
 */
@Entity
@Table(name = "payments", schema = "solution")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Payment {

    /**
     * Уникальный идентификатор платежа.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Пользователь, совершивший платёж.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /**
     * Кабинет, к которому относится платёж.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cabinet_id")
    private Cabinet cabinet;

    /**
     * Подписка, за которую произведён платёж.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subscription_id")
    private Subscription subscription;

    /**
     * Код тарифа на момент оплаты (snapshot).
     */
    @Column(name = "plan_code", length = 50)
    private String planCode;

    /**
     * Название тарифа на момент оплаты (snapshot).
     */
    @Column(name = "plan_name", length = 255)
    private String planName;

    /**
     * Длительность периода на момент оплаты (snapshot).
     */
    @Column(name = "period_days")
    private Integer periodDays;

    /**
     * Тип периода на момент оплаты (snapshot).
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "period_type", length = 20)
    private PlanPeriodType periodType;

    /**
     * Сумма платежа.
     */
    @Column(name = "amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    /**
     * Валюта платежа (ISO 4217).
     */
    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    /**
     * Внешний идентификатор в платёжной системе.
     */
    @Column(name = "external_id")
    private String externalId;

    /**
     * Описание платежа.
     */
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    /**
     * Статус платежа.
     */
    @Column(name = "status", nullable = false, length = 20)
    private String status;

    /**
     * Дата и время успешной оплаты.
     */
    @Column(name = "paid_at")
    private LocalDateTime paidAt;

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

    /**
     * Дополнительные данные платежа (JSON).
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private String metadata;
}
