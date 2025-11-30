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
 * Сущность баркода товара.
 */
@Entity
@Table(name = "product_barcodes", schema = "solution",
        uniqueConstraints = @UniqueConstraint(columnNames = {"nm_id", "sku"}))
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductBarcode {

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
     * ID размера (chrtID).
     */
    @Column(name = "chrt_id")
    private Long chrtId;

    /**
     * Баркод товара (sku).
     */
    @Column(name = "sku", nullable = false, length = 255)
    private String sku;

    /**
     * Технический размер.
     */
    @Column(name = "tech_size", length = 50)
    private String techSize;

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

