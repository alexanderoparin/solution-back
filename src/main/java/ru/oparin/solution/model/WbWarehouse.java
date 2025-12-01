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
     * Название склада.
     */
    @Column(name = "name", nullable = false, length = 255)
    private String name;

    /**
     * Адрес склада.
     */
    @Column(name = "address", length = 500)
    private String address;

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
