package ru.oparin.solution.dto.wb;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Элемент отзыва в ответе API отзывов WB.
 * GET /api/v1/feedbacks — data.feedbacks[]
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class FeedbackItem {
    private Integer productValuation;
    private FeedbackProductDetails productDetails;
}
