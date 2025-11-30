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

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Сущность остатков товаров на складах продавца.
 */
@Entity
@Table(name = "product_stocks", schema = "solution",
        uniqueConstraints = @UniqueConstraint(columnNames = {"nm_id", "warehouse_id", "sku", "date"}))
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
     * ID склада продавца.
     */
    @Column(name = "warehouse_id", nullable = false)
    private Long warehouseId;

    /**
     * Баркод товара (sku).
     */
    @Column(name = "sku", nullable = false, length = 255)
    private String sku;

    /**
     * Количество товара на складе.
     */
    @Column(name = "amount", nullable = false)
    private Integer amount;

    /**
     * Дата, за которую сохранены остатки (вчерашняя дата).
     */
    @Column(name = "date", nullable = false)
    private LocalDate date;

    /**
     * Дата создания записи в БД.
     */
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * Дата последнего обновления записи в БД.
     */
    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}

