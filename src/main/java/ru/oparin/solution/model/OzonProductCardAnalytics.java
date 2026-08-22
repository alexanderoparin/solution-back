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

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cabinet_id", nullable = false)
    private Cabinet cabinet;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "sku")
    private Long sku;

    @Column(name = "date", nullable = false)
    private LocalDate date;

    @Column(name = "ordered_units")
    private Integer orderedUnits;

    @Column(name = "revenue", precision = 19, scale = 2)
    private BigDecimal revenue;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
