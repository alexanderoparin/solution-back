package ru.oparin.solution.model;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Карточка товара Ozon, синхронизированная из Seller API.
 */
@Entity
@Table(name = "ozon_product_cards", schema = "solution")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OzonProductCard {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cabinet_id", nullable = false)
    private Cabinet cabinet;

    /** Идентификатор товара Ozon. */
    @Column(name = "product_id", nullable = false)
    private Long productId;

    /** Артикул продавца. */
    @Column(name = "offer_id", length = 255)
    private String offerId;

    /** SKU Ozon. */
    @Column(name = "sku")
    private Long sku;

    @Column(length = 500)
    private String title;

    @Column(name = "photo_url", length = 1000)
    private String photoUrl;

    /**
     * Контент-рейтинг Ozon (0–100) из {@code POST /v1/product/rating-by-sku}.
     */
    @Column(name = "content_rating", precision = 5, scale = 2)
    private BigDecimal contentRating;

    /** Время последней успешной записи контент-рейтинга. */
    @Column(name = "content_rating_synced_at")
    private LocalDateTime contentRatingSyncedAt;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
