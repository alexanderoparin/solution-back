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
 * Ежедневная агрегированная статистика рекламной кампании Ozon.
 * Источник: GET {@code /api/client/statistics/daily/json}.
 */
@Entity
@Table(
        name = "ozon_promotion_campaign_statistics",
        schema = "solution",
        uniqueConstraints = @UniqueConstraint(columnNames = {"campaign_id", "date"})
)
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OzonPromotionCampaignStatistics {

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
     * Расход (руб.).
     */
    @Column(name = "spend", precision = 19, scale = 2)
    private BigDecimal spend;

    /**
     * Средняя ставка.
     */
    @Column(name = "avg_bid", precision = 19, scale = 4)
    private BigDecimal avgBid;

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
