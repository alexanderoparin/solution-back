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
     * «Оценка карточки» продавца в отчёте item-rating (другая шкала, не звёзды витрины).
     * Для отображения звёзд на странице артикула не используем.
     */
    @JsonProperty("rating")
    private Double rating;

    /**
     * Рейтинг по отзывам (звёзды 1–5 на странице артикула WB).
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
