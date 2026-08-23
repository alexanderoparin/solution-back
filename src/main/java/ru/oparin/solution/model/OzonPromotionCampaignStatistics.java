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
 * Ежедневная статистика рекламной кампании Ozon Performance API.
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

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "campaign_id", nullable = false, referencedColumnName = "campaign_id")
    private OzonPromotionCampaign campaign;

    @Column(name = "date", nullable = false)
    private LocalDate date;

    @Column(name = "views")
    private Integer views;

    @Column(name = "clicks")
    private Integer clicks;

    @Column(name = "spend", precision = 19, scale = 2)
    private BigDecimal spend;

    @Column(name = "avg_bid", precision = 19, scale = 4)
    private BigDecimal avgBid;

    @Column(name = "orders")
    private Integer orders;

    @Column(name = "orders_money", precision = 19, scale = 2)
    private BigDecimal ordersMoney;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
