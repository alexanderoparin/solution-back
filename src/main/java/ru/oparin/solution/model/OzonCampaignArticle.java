package ru.oparin.solution.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * Связь рекламной кампании Ozon и SKU.
 */
@Entity
@Table(name = "ozon_campaign_articles", schema = "solution")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@IdClass(OzonCampaignArticleId.class)
public class OzonCampaignArticle {

    @Id
    @Column(name = "campaign_id", nullable = false)
    private Long campaignId;

    @Id
    @Column(name = "sku", nullable = false)
    private Long sku;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "campaign_id", nullable = false, insertable = false, updatable = false, referencedColumnName = "campaign_id")
    private OzonPromotionCampaign campaign;

    /**
     * product_id из каталога (если SKU сопоставлен с карточкой).
     */
    @Column(name = "product_id")
    private Long productId;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
