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
 * Поисковый запрос (кластер) в рекламной кампании Ozon за один день.
 * Источник: async POST {@code /api/client/statistics/phrases}.
 */
@Entity
@Table(
        name = "ozon_promotion_campaign_search_phrase_statistics",
        schema = "solution",
        uniqueConstraints = @UniqueConstraint(columnNames = {"campaign_id", "date", "search_phrase"})
)
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OzonPromotionCampaignSearchPhraseStatistics {

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
     * SKU из отчёта; может быть {@code null}, если отчёт без разбивки по товару.
     */
    @Column(name = "sku")
    private Long sku;

    /**
     * Дата статистики.
     */
    @Column(name = "date", nullable = false)
    private LocalDate date;

    /**
     * Поисковый запрос (кластер).
     */
    @Column(name = "search_phrase", nullable = false)
    private String searchPhrase;

    /**
     * Средняя позиция в выдаче.
     */
    @Column(name = "avg_pos", precision = 12, scale = 4)
    private BigDecimal avgPos;

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
