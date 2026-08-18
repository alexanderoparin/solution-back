package ru.oparin.solution.dto.wb;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.util.List;

/**
 * Ответ POST /api/analytics/v2/item-rating.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ItemRatingResponse {

    @JsonProperty("data")
    private Data data;

    /**
     * Страница отчёта: {@code data.items} либо пустой список.
     */
    public List<ItemRatingCard> resolveItems() {
        if (data == null) {
            return List.of();
        }
        return data.resolveItems();
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Data {
        @JsonProperty("items")
        private List<ItemRatingCard> items;

        /**
         * Строки отчёта текущей страницы.
         */
        public List<ItemRatingCard> resolveItems() {
            return items != null ? items : List.of();
        }
    }
}
