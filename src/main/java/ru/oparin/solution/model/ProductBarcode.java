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
 * Сохраняется из информации о карточке товара (CardDto.Size).
 * Баркод является уникальным ключом.
 */
@Entity
@Table(name = "product_barcodes", schema = "solution")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductBarcode {

    /**
     * Баркод товара (из массива skus в CardDto.Size).
     * Первичный ключ.
     */
    @Id
    @Column(name = "barcode", nullable = false, length = 255)
    private String barcode;

    /**
     * Артикул WB (nmID).
     */
    @Column(name = "nm_id", nullable = false)
    private Long nmId;

    /**
     * ID характеристики размера (chrtID).
     */
    @Column(name = "chrt_id", nullable = false)
    private Long chrtId;

    /**
     * Технический размер (techSize, например "L").
     */
    @Column(name = "tech_size", length = 50)
    private String techSize;

    /**
     * Российский размер (wbSize, например "48").
     */
    @Column(name = "wb_size", length = 50)
    private String wbSize;

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

