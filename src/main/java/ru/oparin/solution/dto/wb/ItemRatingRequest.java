package ru.oparin.solution.dto.wb;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

/**
 * Запрос отчёта «Оценка товара».
 * Эндпоинт: POST /api/analytics/v1/item-rating
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ItemRatingRequest {

    @JsonProperty("currentPeriod")
    private Period currentPeriod;

    @JsonProperty("orderBy")
    private OrderBy orderBy;

    @JsonProperty("limit")
    private Integer limit;

    @JsonProperty("offset")
    private Integer offset;

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Period {
        @JsonProperty("start")
        private String start;

        @JsonProperty("end")
        private String end;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderBy {
        @JsonProperty("field")
        private String field;

        @JsonProperty("mode")
        private String mode;
    }
}
