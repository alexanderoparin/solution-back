package ru.oparin.solution.dto.analytics;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

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
}

