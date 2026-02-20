package ru.oparin.solution.model;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * Связь рекламной кампании и артикула.
 */
@Entity
@Table(name = "campaign_articles", schema = "solution")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
@IdClass(CampaignArticleId.class)
public class CampaignArticle {

    /**
     * ID рекламной кампании.
     */
    @Id
    @Column(name = "campaign_id", nullable = false)
    private Long campaignId;

    /**
     * Артикул товара (nmID).
     */
    @Id
    @Column(name = "nm_id", nullable = false)
    private Long nmId;

    /**
     * Рекламная кампания.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "campaign_id", nullable = false, insertable = false, updatable = false, referencedColumnName = "advert_id")
    private PromotionCampaign campaign;

    /**
     * Артикул товара.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "nm_id", nullable = false, insertable = false, updatable = false, referencedColumnName = "nm_id")
    private ProductCard productCard;

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

