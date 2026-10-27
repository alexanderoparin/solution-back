package ru.oparin.solution.dto.wb;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * Ответ публичного catalog API карточек WB ({@code /cards/v2/detail}).
 */
@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class WbPublicCardDetailResponse {

    private Data data;

    @Getter
    @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Data {
        private List<Product> products;
    }

    @Getter
    @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Product {
        /** Артикул WB (nmId). */
        private Long id;

        /** Рейтинг по отзывам на витрине (звёзды 1–5). */
        @JsonProperty("reviewRating")
        private Double reviewRating;

        /** Запасное имя поля в части ответов. */
        @JsonProperty("nmReviewRating")
        private Double nmReviewRating;

        /**
         * Выбирает рейтинг витрины из известных полей ответа.
         */
        public Double resolveReviewRating() {
            if (reviewRating != null) {
                return reviewRating;
            }
            return nmReviewRating;
        }
    }
}
