package ru.oparin.solution.dto.wb;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Данные товара в ответе API отзывов WB.
 * GET /api/v1/feedbacks
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class FeedbackProductDetails {
    private Long nmId;
    private Long imtId;
    private String productName;
    private String supplierArticle;
    private String brandName;
}
