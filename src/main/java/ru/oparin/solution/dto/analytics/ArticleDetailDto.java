package ru.oparin.solution.dto.analytics;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * DTO для детальной информации об артикуле.
 * Используется на странице детального просмотра артикула.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArticleDetailDto {
    /**
     * Артикул WB (nmID).
     */
    private Long nmId;
    
    /**
     * IMT ID товара.
     */
    private Long imtId;
    
    /**
     * Название товара.
     */
    private String title;
    
    /**
     * Бренд товара.
     */
    private String brand;
    
    /**
     * Название категории товара.
     */
    private String subjectName;
    
    /**
     * Артикул продавца.
     */
    private String vendorCode;
    
    /**
     * URL миниатюры первой фотографии товара.
     */
    private String photoTm;
    
    /**
     * Рейтинг товара.
     */
    private BigDecimal rating;
    
    /**
     * Количество отзывов.
     */
    private Integer reviewsCount;
    
    /**
     * URL карточки товара на Wildberries.
     */
    private String productUrl;
    
    /**
     * Дата создания карточки товара.
     */
    private LocalDateTime createdAt;
    
    /**
     * Дата последнего обновления карточки товара.
     */
    private LocalDateTime updatedAt;
}

