package ru.oparin.solution.dto.wb;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

/**
 * Элемент data.cards[] в отчёте item-rating.
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

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FeedbackRating {
        @JsonProperty("current")
        private Double current;
    }
}
