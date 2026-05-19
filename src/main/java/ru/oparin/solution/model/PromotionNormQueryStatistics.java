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
 * Статистика по поисковому кластеру (norm query) внутри рекламной кампании за один день.
 * <p>
 * Источник: POST {@code /adv/v1/normquery/stats} (WB Promotion API).
 * Одна строка соответствует паре «кампания + артикул + дата + поисковая фраза».
 */
@Entity
@Table(name = "promotion_norm_query_statistics", schema = "solution",
        uniqueConstraints = @UniqueConstraint(columnNames = {"campaign_id", "nm_id", "date", "norm_query"}))
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PromotionNormQueryStatistics {

    /**
     * ID записи в БД.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    /**
     * Рекламная кампания (advert_id).
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "campaign_id", nullable = false, referencedColumnName = "advert_id")
    private PromotionCampaign campaign;

    /**
     * Артикул WB (nmId).
     */
    @Column(name = "nm_id", nullable = false)
    private Long nmId;

    /**
     * Дата, за которую собрана статистика.
     */
    @Column(name = "date", nullable = false)
    private LocalDate date;

    /**
     * Поисковый кластер (нормализованная поисковая фраза из WB).
     */
    @Column(name = "norm_query", nullable = false)
    private String normQuery;

    /**
     * Средняя позиция товара в поисковой выдаче.
     */
    @Column(name = "avg_pos", precision = 12, scale = 4)
    private BigDecimal avgPos;

    /**
     * Количество кликов.
     */
    @Column(name = "clicks")
    private Integer clicks;

    /**
     * Количество добавлений в корзину.
     */
    @Column(name = "atbs")
    private Integer atbs;

    /**
     * Количество заказов.
     */
    @Column(name = "orders")
    private Integer orders;

    /**
     * Количество заказанных товаров, шт. (штуки / SKU из ответа WB).
     */
    @Column(name = "shks")
    private Integer shks;

    /**
     * Затраты на продвижение в кластере, руб.
     */
    @Column(name = "spend", precision = 14, scale = 2)
    private BigDecimal spend;

    /**
     * Средняя стоимость клика, руб.
     */
    @Column(name = "cpc", precision = 12, scale = 4)
    private BigDecimal cpc;

    /**
     * Просмотры. Для кампаний с оплатой CPC может быть {@code null}.
     */
    @Column(name = "views")
    private Integer views;

    /**
     * CTR, %. Для кампаний с оплатой CPC может быть {@code null}.
     */
    @Column(name = "ctr", precision = 10, scale = 4)
    private BigDecimal ctr;

    /**
     * CPM (стоимость за тысячу показов), руб. Для CPC-кампаний может быть {@code null}.
     */
    @Column(name = "cpm", precision = 12, scale = 4)
    private BigDecimal cpm;

    /**
     * Дата и время создания записи в БД.
     */
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * Дата и время последнего обновления записи в БД.
     */
    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
