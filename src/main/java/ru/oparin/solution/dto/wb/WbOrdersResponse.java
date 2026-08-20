package ru.oparin.solution.dto.wb;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.util.List;

/**
 * DTO для ответа с заказами от /api/v1/supplier/orders.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class WbOrdersResponse {
    
    /**
     * Список заказов.
     */
    private List<Order> orders;

    /**
     * Внутренний класс для информации о заказе.
     */
    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Order {
        /**
         * Артикул WB (nmID).
         */
        @JsonProperty("nmId")
        private Long nmId;

        /**
         * СПП (Скидка постоянного покупателя) в процентах.
         */
        @JsonProperty("spp")
        private Integer spp;

        /**
         * Дата заказа.
         */
        @JsonProperty("date")
        private String date;

        /**
         * Дата последнего изменения.
         */
        @JsonProperty("lastChangeDate")
        private String lastChangeDate;

        // Остальные поля не используются, но могут быть в ответе
    }
}
