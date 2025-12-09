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
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Сущность аналитики воронки продаж для карточки товара.
 */
@Entity
@Table(name = "product_card_analytics", schema = "solution")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductCardAnalytics {

    /**
     * Уникальный идентификатор записи.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Карточка товара.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "nm_id", nullable = false)
    private ProductCard productCard;

    /**
     * Дата аналитики.
     */
    @Column(name = "date", nullable = false)
    private LocalDate date;

    /**
     * Переходы в карточку.
     */
    @Column(name = "open_card")
    private Integer openCard;

    /**
     * Положили в корзину, шт.
     */
    @Column(name = "add_to_cart")
    private Integer addToCart;

    /**
     * Заказали товаров, шт.
     */
    @Column(name = "orders")
    private Integer orders;

    /**
     * Заказали на сумму, руб.
     */
    @Column(name = "orders_sum", precision = 19, scale = 2)
    private BigDecimal ordersSum;

    /**
     * Дата создания записи.
     */
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * Дата последнего обновления записи.
     */
    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}

