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

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Сущность склада WB.
 */
@Entity
@Table(name = "wb_warehouses", schema = "solution")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WbWarehouse {

    /**
     * ID склада WB (из API).
     */
    @Id
    @Column(name = "id")
    private Integer id;

    /**
     * Адрес склада.
     */
    @Column(name = "address", length = 500)
    private String address;

    /**
     * Название склада.
     */
    @Column(name = "name", nullable = false, length = 255)
    private String name;

    /**
     * Город.
     */
    @Column(name = "city", length = 255)
    private String city;

    /**
     * Долгота.
     */
    @Column(name = "longitude", precision = 10, scale = 6)
    private BigDecimal longitude;

    /**
     * Широта.
     */
    @Column(name = "latitude", precision = 10, scale = 6)
    private BigDecimal latitude;

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
     * Федеральный округ.
     */
    @Column(name = "federal_district", length = 255)
    private String federalDistrict;

    /**
     * Выбран ли склад.
     */
    @Column(name = "selected")
    private Boolean selected;

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

