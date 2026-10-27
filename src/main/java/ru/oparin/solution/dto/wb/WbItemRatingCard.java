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
public class WbItemRatingCard {

    @JsonProperty("nmId")
    private Long nmId;

    /**
     * Рейтинг карточки товара (то же значение, что на витрине / странице артикула WB), шкала 1–5.
     */
    @JsonProperty("rating")
    private Double rating;

    /**
     * Рейтинг по отзывам из отчёта «Оценка товара» (может отличаться от {@link #rating}).
     */
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
