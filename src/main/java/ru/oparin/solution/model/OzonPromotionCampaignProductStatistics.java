package ru.oparin.solution.model;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Дневная статистика SKU внутри рекламной кампании Ozon.
 * Источник: async POST {@code /api/client/statistics} (groupBy=DATE).
 */
@Entity
@Table(
        name = "ozon_promotion_campaign_product_statistics",
        schema = "solution",
        uniqueConstraints = @UniqueConstraint(columnNames = {"campaign_id", "sku", "date"})
)
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OzonPromotionCampaignProductStatistics {

    /**
     * Уникальный идентификатор записи.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Кампания, к которой относится статистика.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "campaign_id", nullable = false, referencedColumnName = "campaign_id")
    private OzonPromotionCampaign campaign;

    /**
     * SKU товара Ozon.
     */
    @Column(name = "sku", nullable = false)
    private Long sku;

    /**
     * Дата статистики.
     */
    @Column(name = "date", nullable = false)
    private LocalDate date;

    /**
     * Показы.
     */
    @Column(name = "views")
    private Integer views;

    /**
     * Клики.
     */
    @Column(name = "clicks")
    private Integer clicks;

    /**
     * CTR — доля кликов от показов.
     */
    @Column(name = "ctr", precision = 10, scale = 4)
    private BigDecimal ctr;

    /**
     * Добавления в корзину.
     */
    @Column(name = "to_cart")
    private Integer toCart;

    /**
     * Средняя цена клика.
     */
    @Column(name = "avg_cpc", precision = 19, scale = 4)
    private BigDecimal avgCpc;

    /**
     * Расход (руб.).
     */
    @Column(name = "spend", precision = 19, scale = 2)
    private BigDecimal spend;

    /**
     * Заказы, шт.
     */
    @Column(name = "orders")
    private Integer orders;

    /**
     * Заказы, сумма (руб.).
     */
    @Column(name = "orders_money", precision = 19, scale = 2)
    private BigDecimal ordersMoney;

    /**
     * Заказы по модели атрибуции, шт.
     */
    @Column(name = "model_orders")
    private Integer modelOrders;

    /**
     * Продажи по модели атрибуции (руб.).
     */
    @Column(name = "model_sales", precision = 19, scale = 2)
    private BigDecimal modelSales;

    /**
     * ДРР — доля рекламных расходов.
     */
    @Column(name = "drr", precision = 10, scale = 4)
    private BigDecimal drr;

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
