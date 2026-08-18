package ru.oparin.solution.dto.wb;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

/**
 * Элемент {@code data.items[]} в отчёте item-rating v2.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ItemRatingCard {

    @JsonProperty("nmId")
    private Long nmId;

    @JsonProperty("feedbackRating")
    private FeedbackRating feedbackRating;

    /**
     * Рейтинг товара по отзывам (шкала 1–5).
     */
    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FeedbackRating {
        /** Текущий рейтинг по отзывам. */
        @JsonProperty("current")
        private Double current;
    }
}
