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
 * Статистика артикула внутри РК WB по дате с разрезами зоны показа и типа конверсии.
 * Источник: GET /api/advert/v4/fullstats; история v3 — строки с placement/conversion_type = {@code all}.
 */
@Entity
@Table(name = "wb_promotion_campaign_statistics_slices", schema = "solution",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_wb_promo_stats_slices",
                columnNames = {"campaign_id", "nm_id", "date", "placement", "conversion_type"}))
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WbPromotionCampaignStatistics {

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
    private WbPromotionCampaign campaign;

    /**
     * Артикул товара (nmId).
     */
    @Column(name = "nm_id", nullable = false)
    private Long nmId;

    /**
     * Дата статистики.
     */
    @Column(name = "date", nullable = false)
    private LocalDate date;

    /**
     * Зона показов: {@code search}, {@code recommendation} или {@code all}.
     */
    @Column(name = "placement", nullable = false, length = 32)
    private String placement;

    /**
     * Тип конверсии: {@code direct}, {@code multicard}, {@code associated} или {@code all}.
     */
    @Column(name = "conversion_type", nullable = false, length = 32)
    private String conversionType;

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
     * Расходы (в рублях).
     */
    @Column(name = "sum", precision = 10, scale = 2)
    private BigDecimal sum;

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
     * CPC (Cost Per Click) - стоимость клика.
     */
    @Column(name = "cpc", precision = 10, scale = 4)
    private BigDecimal cpc;

    /**
     * CPA (Cost Per Action) - стоимость заказа (в рублях).
     */
    @Column(name = "cpa", precision = 10, scale = 2)
    private BigDecimal cpa;

    /**
     * Добавлено в корзину.
     */
    @Column(name = "atbs")
    private Integer atbs;

    /**
     * Отменено заказов.
     */
    @Column(name = "canceled")
    private Integer canceled;

    /**
     * ШК (штрих-коды) / заказано товаров.
     */
    @Column(name = "shks")
    private Integer shks;

    /**
     * Сумма заказов (в рублях).
     */
    @Column(name = "orders_sum", precision = 10, scale = 2)
    private BigDecimal ordersSum;

    /**
     * Сумма заказов (sumPrice из API).
     */
    @Column(name = "sum_price", precision = 10, scale = 2)
    private BigDecimal sumPrice;

    /**
     * Средняя позиция в поиске (только search + direct).
     */
    @Column(name = "avg_pos", precision = 10, scale = 2)
    private BigDecimal avgPos;

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
