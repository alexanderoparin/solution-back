package ru.oparin.solution.model;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Ежедневная аналитика продаж товара Ozon.
 * Источник: {@code POST /v1/analytics/data}.
 */
@Entity
@Table(name = "ozon_product_card_analytics", schema = "solution",
        uniqueConstraints = @UniqueConstraint(columnNames = {"cabinet_id", "product_id", "date"}))
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OzonProductCardAnalytics {

    /**
     * Уникальный идентификатор записи.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Кабинет, которому принадлежит товар.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cabinet_id", nullable = false)
    private Cabinet cabinet;

    /**
     * Идентификатор товара Ozon.
     */
    @Column(name = "product_id", nullable = false)
    private Long productId;

    /**
     * SKU Ozon.
     */
    @Column(name = "sku")
    private Long sku;

    /**
     * Дата аналитики.
     */
    @Column(name = "date", nullable = false)
    private LocalDate date;

    /**
     * Заказано единиц, шт.
     */
    @Column(name = "ordered_units")
    private Integer orderedUnits;

    /**
     * Выручка (руб.).
     */
    @Column(name = "revenue", precision = 19, scale = 2)
    private BigDecimal revenue;

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
