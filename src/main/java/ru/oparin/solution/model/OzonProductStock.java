package ru.oparin.solution.model;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * Остаток товара Ozon на типе склада (FBO, FBS и др.).
 * Источник: {@code POST /v4/product/info/stocks}.
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
     * Тип склада (FBO, FBS и т.д.).
     */
    @Column(name = "stock_type", nullable = false, length = 32)
    private String stockType;

    /**
     * Доступное количество, шт.
     */
    @Column(name = "present", nullable = false)
    private Integer present;

    /**
     * Зарезервированное количество, шт.
     */
    @Column(name = "reserved", nullable = false)
    private Integer reserved;

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
