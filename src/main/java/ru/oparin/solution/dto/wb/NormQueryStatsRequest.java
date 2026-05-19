package ru.oparin.solution.dto.wb;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.util.List;

/**
 * DTO запроса статистики по поисковым кластерам.
 * <p>
 * Эндпоинт: POST {@code /adv/v1/normquery/stats}
 * <br>
 * Документация: promotion API, {@code V1GetNormQueryStatsRequest}.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NormQueryStatsRequest {

    /**
     * Дата начала периода (формат: {@code yyyy-MM-dd}).
     */
    private String from;

    /**
     * Дата окончания периода включительно (формат: {@code yyyy-MM-dd}).
     */
    private String to;

    /**
     * Список пар «кампания + артикул» (не более 100 элементов в одном запросе по лимитам WB).
     */
    private List<Item> items;

    /**
     * Элемент запроса: идентификатор кампании и артикула.
     */
    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Item {

        /**
         * ID рекламной кампании (advertId).
         */
        @JsonProperty("advertId")
        private Long advertId;

        /**
         * Артикул WB (nmId).
         */
        @JsonProperty("nmId")
        private Long nmId;
    }
}
