package ru.oparin.solution.model;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * Склад продавца WB (Marketplace API).
 * {@code warehouseId} — ID склада продавца, не путать с {@link WbWarehouse} и {@code officeId}.
 */
@Entity
@Table(name = "seller_warehouses", schema = "solution",
        uniqueConstraints = @UniqueConstraint(columnNames = {"cabinet_id", "warehouse_id"}))
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SellerWarehouse {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    /**
     * Кабинет, которому принадлежит склад продавца.
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
     * Название склада продавца.
     */
    @Column(name = "name", nullable = false, length = 255)
    private String name;

    /**
     * ID офиса WB, к которому привязан склад (другой справочник, чем {@link WbWarehouse}).
     */
    @Column(name = "office_id")
    private Long officeId;

    /**
     * Тип товара: 1 — МГТ, 2 — СГТ, 3 — КГТ+.
     */
    @Column(name = "cargo_type")
    private Integer cargoType;

    /**
     * Тип доставки: 1 — FBS, 2 — DBS, 3 — DBW, 5 — C&C, 6 — EDBS.
     */
    @Column(name = "delivery_type")
    private Integer deliveryType;

    /**
     * Склад удаляется на стороне WB.
     */
    @Builder.Default
    @Column(name = "is_deleting", nullable = false)
    private Boolean isDeleting = false;

    /**
     * Данные склада обновляются на стороне WB.
     */
    @Builder.Default
    @Column(name = "is_processing", nullable = false)
    private Boolean isProcessing = false;

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
