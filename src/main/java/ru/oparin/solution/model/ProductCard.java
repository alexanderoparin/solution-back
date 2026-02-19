package ru.oparin.solution.model;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Сущность карточки товара из WB API.
 */
@Entity
@Table(name = "product_cards", schema = "solution")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductCard {

    /**
     * Уникальный идентификатор карточки товара (nmID из WB API).
     */
    @Id
    @Column(name = "nm_id")
    private Long nmId;

    /**
     * ID карточки товара (imtID из WB API).
     * Карточки с одинаковым imtID считаются объединёнными.
     */
    @Column(name = "imt_id")
    private Long imtId;

    /**
     * Продавец, владелец карточки (оставляем для удобства).
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seller_id", nullable = false)
    private User seller;

    /**
     * Кабинет, которому принадлежит артикул. Один артикул — один кабинет.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cabinet_id", nullable = false)
    private Cabinet cabinet;

    /**
     * Название товара.
     */
    @Column(length = 500)
    private String title;

    /**
     * Название категории товара.
     */
    @Column(name = "subject_name", length = 255)
    private String subjectName;

    /**
     * Бренд товара.
     */
    @Column(length = 255)
    private String brand;

    /**
     * Артикул продавца.
     */
    @Column(name = "vendor_code", length = 255)
    private String vendorCode;

    /**
     * URL миниатюры первой фотографии товара.
     */
    @Column(name = "photo_tm", length = 1000)
    private String photoTm;

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

    /**
     * Средний рейтинг по обработанным отзывам WB (1–5).
     * Заполняется синхронизацией с API отзывов (категория «Вопросы и отзывы»).
     */
    @Column(name = "rating", precision = 3, scale = 2)
    private BigDecimal rating;

    /**
     * Количество обработанных отзывов по товару.
     */
    @Column(name = "reviews_count")
    private Integer reviewsCount;
}

