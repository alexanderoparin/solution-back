package ru.oparin.solution.model;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * Остаток размера товара на складе продавца (FBS).
 * Ключ снимка: кабинет + склад продавца + {@code chrtId}.
 */
@Entity
@Table(name = "wb_product_fbs_stocks", schema = "solution",
        uniqueConstraints = @UniqueConstraint(columnNames = {"cabinet_id", "warehouse_id", "chrt_id"}))
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WbProductFbsStock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    /**
     * Кабинет, которому принадлежат остатки.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cabinet_id", nullable = false)
    private Cabinet cabinet;

    /**
     * ID склада продавца в WB.
     */
    @Column(name = "warehouse_id", nullable = false)
    private Long warehouseId;

    /**
     * Артикул WB (nmID), если размер найден в {@link WbProductBarcode}.
     */
    @Column(name = "nm_id")
    private Long nmId;

    /**
     * ID размера товара (chrtId) в WB.
     */
    @Column(name = "chrt_id", nullable = false)
    private Long chrtId;

    /**
     * Баркод (sku) из ответа WB или из {@link WbProductBarcode}.
     */
    @Column(name = "sku", length = 255)
    private String sku;

    /**
     * Количество товара на складе продавца.
     */
    @Column(name = "amount", nullable = false)
    private Integer amount;

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
