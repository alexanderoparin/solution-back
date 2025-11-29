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
 * Сущность статистики рекламной кампании из WB API.
 */
@Entity
@Table(name = "promotion_campaign_statistics", schema = "solution",
        uniqueConstraints = @UniqueConstraint(columnNames = {"campaign_id", "date"}))
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PromotionCampaignStatistics {

    /**
     * ID записи статистики.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    /**
     * Кампания, к которой относится статистика.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "campaign_id", nullable = false, referencedColumnName = "advert_id")
    private PromotionCampaign campaign;

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
     * CTR (Click-Through Rate) - процент кликов от показов.
     */
    @Column(name = "ctr", precision = 10, scale = 4)
    private BigDecimal ctr;

    /**
     * Расходы (в копейках).
     */
    @Column(name = "sum")
    private Long sum;

    /**
     * Заказы.
     */
    @Column(name = "orders")
    private Integer orders;

    /**
     * CR (Conversion Rate) - процент заказов от кликов.
     */
    @Column(name = "cr", precision = 10, scale = 4)
    private BigDecimal cr;

    /**
     * CPA (Cost Per Action) - стоимость заказа (в копейках).
     */
    @Column(name = "cpa", precision = 10, scale = 2)
    private BigDecimal cpa;

    /**
     * Сумма заказов (в копейках).
     */
    @Column(name = "orders_sum")
    private Long ordersSum;

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

