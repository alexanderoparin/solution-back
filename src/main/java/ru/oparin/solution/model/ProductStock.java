package ru.oparin.solution.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * Сущность остатков товаров на складах WB.
 * Данные перезаписываются каждый день, время записи фиксируется в created_at и updated_at.
 */
@Entity
@Table(name = "product_stocks", schema = "solution",
        uniqueConstraints = @UniqueConstraint(columnNames = {"cabinet_id", "nm_id", "warehouse_id", "barcode"}))
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductStock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    /**
     * Артикул WB (nmID).
     */
    @Column(name = "nm_id", nullable = false)
    private Long nmId;

    /**
     * Кабинет (остатки привязаны к кабинету).
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cabinet_id", nullable = false)
    private Cabinet cabinet;

    /**
     * ID склада WB.
     */
    @Column(name = "warehouse_id", nullable = false)
    private Long warehouseId;

    /**
     * Баркод товара (из product_barcodes).
     */
    @Column(name = "barcode", nullable = false, length = 255)
    private String barcode;

    /**
     * Количество товара на складе.
     */
    @Column(name = "amount", nullable = false)
    private Integer amount;

    /**
     * Дата создания записи в БД (фиксирует время первой записи).
     */
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * Дата последнего обновления записи в БД (фиксирует время последнего обновления).
     */
    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}

