package ru.oparin.solution.model;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Снимок цены товара Ozon на дату (без SPP).
 * Источник: {@code POST /v5/product/info/prices}.
 */
@Entity
@Table(name = "ozon_product_price_history", schema = "solution",
        uniqueConstraints = @UniqueConstraint(columnNames = {"cabinet_id", "product_id", "date"}))
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OzonProductPriceHistory {

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
     * Дата снимка цены.
     */
    @Column(name = "date", nullable = false)
    private LocalDate date;

    /**
     * Цена продавца.
     */
    @Column(name = "price", nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    /**
     * Зачёркнутая цена (до скидки).
     */
    @Column(name = "old_price", precision = 12, scale = 2)
    private BigDecimal oldPrice;

    /**
     * Цена с учётом акций Ozon.
     */
    @Column(name = "marketing_price", precision = 12, scale = 2)
    private BigDecimal marketingPrice;

    /**
     * Минимальная допустимая цена.
     */
    @Column(name = "min_price", precision = 12, scale = 2)
    private BigDecimal minPrice;

    /**
     * Код валюты (по умолчанию RUB).
     */
    @Column(name = "currency_code", nullable = false, length = 8)
    @Builder.Default
    private String currencyCode = "RUB";

    /**
     * Дата создания записи.
     */
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
