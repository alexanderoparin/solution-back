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
 * Сущность склада продавца.
 */
@Entity
@Table(name = "seller_warehouses", schema = "solution")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SellerWarehouse {

    /**
     * ID склада продавца (из API).
     */
    @Id
    @Column(name = "id")
    private Long id;

    /**
     * Продавец, владелец склада.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seller_id", nullable = false)
    private User seller;

    /**
     * Название склада.
     */
    @Column(name = "name", nullable = false, length = 255)
    private String name;

    /**
     * ID склада WB, к которому привязан склад продавца.
     */
    @Column(name = "office_id")
    private Integer officeId;

    /**
     * Тип груза.
     */
    @Column(name = "cargo_type")
    private Integer cargoType;

    /**
     * Тип доставки.
     */
    @Column(name = "delivery_type")
    private Integer deliveryType;

    /**
     * Флаг удаления склада.
     */
    @Column(name = "is_deleting")
    private Boolean isDeleting;

    /**
     * Флаг обработки склада.
     */
    @Column(name = "is_processing")
    private Boolean isProcessing;

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

