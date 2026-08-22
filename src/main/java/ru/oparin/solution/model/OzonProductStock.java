package ru.oparin.solution.model;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * Остаток товара Ozon на типе склада (FBO/FBS и др.).
 */
@Entity
@Table(name = "ozon_product_stocks", schema = "solution",
        uniqueConstraints = @UniqueConstraint(columnNames = {"cabinet_id", "product_id", "sku", "stock_type"}))
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OzonProductStock {

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

    @Column(name = "stock_type", nullable = false, length = 32)
    private String stockType;

    @Column(name = "present", nullable = false)
    private Integer present;

    @Column(name = "reserved", nullable = false)
    private Integer reserved;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
