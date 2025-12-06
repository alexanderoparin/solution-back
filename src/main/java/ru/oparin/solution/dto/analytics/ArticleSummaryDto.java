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
    private Long nmId;
    private String title;
    private String brand;
    private String subjectName;
    private String photoTm; // URL миниатюры первой фотографии товара
}

