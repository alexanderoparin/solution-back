package ru.oparin.solution.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Сущность истории изменения цен товаров.
 */
@Entity
@Table(name = "product_price_history", schema = "solution",
        uniqueConstraints = @UniqueConstraint(columnNames = {"nm_id", "date", "size_id"}))
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductPriceHistory {

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
     * Дата, за которую сохранена цена (вчерашняя дата).
     */
    @Column(name = "date", nullable = false)
    private LocalDate date;

    /**
     * ID размера (null если цена одинаковая для всех размеров).
     */
    @Column(name = "size_id")
    private Long sizeId;

    /**
     * Название размера (42, M, L и т.д.).
     */
    @Column(name = "tech_size_name", length = 50)
    private String techSizeName;

    /**
     * Цена до скидки (в рублях).
     */
    @Column(name = "price", nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    /**
     * Цена со скидкой продавца (в рублях).
     */
    @Column(name = "discounted_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal discountedPrice;

    /**
     * Цена со скидкой WB Клуба (в рублях).
     */
    @Column(name = "club_discounted_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal clubDiscountedPrice;

    /**
     * Цена с СПП (Скидка постоянного покупателя) в рублях.
     * СПП - это скидка, которую дает сам Wildberries постоянным покупателям.
     */
    @Column(name = "spp_price", precision = 10, scale = 2)
    private BigDecimal sppPrice;

    /**
     * Скидка продавца (%).
     */
    @Column(name = "discount", nullable = false)
    private Integer discount;

    /**
     * Скидка WB Клуба (%).
     */
    @Column(name = "club_discount", nullable = false)
    private Integer clubDiscount;

    /**
     * Можно ли редактировать цену размера.
     */
    @Column(name = "editable_size_price")
    private Boolean editableSizePrice;

    /**
     * Плохой оборот товара.
     */
    @Column(name = "is_bad_turnover")
    private Boolean isBadTurnover;

    /**
     * Дата создания записи в БД.
     */
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}

