package ru.oparin.solution.dto.analytics;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

import java.math.BigDecimal;

/**
 * DTO для краткой информации об артикуле в сводной таблице.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.ALWAYS)
public class ArticleSummaryDto {
    /**
     * Артикул WB (nmID).
     */
    private Long nmId;
    
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
     * URL миниатюры первой фотографии товара.
     */
    private String photoTm;

    /**
     * Артикул продавца.
     */
    private String vendorCode;

    /**
     * Средний рейтинг по отзывам WB (1–5).
     */
    private BigDecimal rating;

    /**
     * Количество отзывов по товару.
     */
    private Integer reviewsCount;
}

