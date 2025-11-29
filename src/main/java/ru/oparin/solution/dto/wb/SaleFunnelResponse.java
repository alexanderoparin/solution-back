package ru.oparin.solution.dto.wb;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

/**
 * DTO для ответа аналитики воронки продаж от WB API.
 * 
 * Эндпоинт: POST /api/analytics/v3/sales-funnel/products/history
 * Документация: https://dev.wildberries.ru/openapi/analytics#tag/Voronka-prodazh
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class SaleFunnelResponse {

    /**
     * Список данных по дням для карточек товаров.
     */
    private List<DailyData> data;

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class DailyData {
        /**
         * Артикул WB (nmID).
         */
        @JsonProperty("nmID")
        private Long nmId;

        /**
         * Дата.
         */
        @JsonProperty("dt")
        private String dt;

        /**
         * Переходы в карточку.
         */
        @JsonProperty("openCardCount")
        private Integer openCardCount;

        /**
         * Положили в корзину, шт.
         */
        @JsonProperty("addToCartCount")
        private Integer addToCartCount;

        /**
         * Заказали товаров, шт.
         */
        @JsonProperty("ordersCount")
        private Integer ordersCount;

        /**
         * Заказали на сумму, руб.
         */
        @JsonProperty("ordersSumRub")
        private BigDecimal ordersSumRub;

        /**
         * Конверсия из переходов в корзину, %.
         */
        @JsonProperty("addToCartConversion")
        private BigDecimal addToCartConversion;

        /**
         * Конверсия из корзины в заказ, %.
         */
        @JsonProperty("cartToOrderConversion")
        private BigDecimal cartToOrderConversion;
    }
}

