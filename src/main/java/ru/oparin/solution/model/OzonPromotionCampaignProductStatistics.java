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
 * Дневная статистика SKU внутри рекламной кампании Ozon Performance API.
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

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "campaign_id", nullable = false, referencedColumnName = "campaign_id")
    private OzonPromotionCampaign campaign;

    @Column(name = "sku", nullable = false)
    private Long sku;

    @Column(name = "date", nullable = false)
    private LocalDate date;

    @Column(name = "views")
    private Integer views;

    @Column(name = "clicks")
    private Integer clicks;

    @Column(name = "ctr", precision = 10, scale = 4)
    private BigDecimal ctr;

    @Column(name = "to_cart")
    private Integer toCart;

    @Column(name = "avg_cpc", precision = 19, scale = 4)
    private BigDecimal avgCpc;

    @Column(name = "spend", precision = 19, scale = 2)
    private BigDecimal spend;

    @Column(name = "orders")
    private Integer orders;

    @Column(name = "orders_money", precision = 19, scale = 2)
    private BigDecimal ordersMoney;

    @Column(name = "model_orders")
    private Integer modelOrders;

    @Column(name = "model_sales", precision = 19, scale = 2)
    private BigDecimal modelSales;

    @Column(name = "drr", precision = 10, scale = 4)
    private BigDecimal drr;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
