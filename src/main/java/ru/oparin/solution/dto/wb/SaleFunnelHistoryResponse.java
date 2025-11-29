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
 * DTO для ответа истории аналитики воронки продаж от WB API.
 * 
 * Эндпоинт: POST /api/analytics/v3/sales-funnel/products/history
 * Реальная структура ответа: массив объектов с полями product и history
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class SaleFunnelHistoryResponse {

    /**
     * Информация о продукте.
     */
    private Product product;

    /**
     * История аналитики по дням.
     */
    private List<HistoryItem> history;

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Product {
        /**
         * Артикул WB (nmID).
         */
        @JsonProperty("nmId")
        private Long nmId;

        /**
         * Название товара.
         */
        private String title;

        /**
         * Артикул продавца.
         */
        @JsonProperty("vendorCode")
        private String vendorCode;

        /**
         * Название бренда.
         */
        @JsonProperty("brandName")
        private String brandName;

        /**
         * ID категории.
         */
        @JsonProperty("subjectId")
        private Integer subjectId;

        /**
         * Название категории.
         */
        @JsonProperty("subjectName")
        private String subjectName;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class HistoryItem {
        /**
         * Дата аналитики (формат YYYY-MM-DD).
         */
        private String date;

        /**
         * Переходы в карточку.
         */
        @JsonProperty("openCount")
        private Integer openCount;

        /**
         * Положили в корзину, шт.
         */
        @JsonProperty("cartCount")
        private Integer cartCount;

        /**
         * Заказали товаров, шт.
         */
        @JsonProperty("orderCount")
        private Integer orderCount;

        /**
         * Заказали на сумму, руб.
         */
        @JsonProperty("orderSum")
        private BigDecimal orderSum;

        /**
         * Выкупили товаров, шт.
         */
        @JsonProperty("buyoutCount")
        private Integer buyoutCount;

        /**
         * Выкупили на сумму, руб.
         */
        @JsonProperty("buyoutSum")
        private BigDecimal buyoutSum;

        /**
         * Процент выкупа, %.
         */
        @JsonProperty("buyoutPercent")
        private BigDecimal buyoutPercent;

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

        /**
         * Добавили в избранное, шт.
         */
        @JsonProperty("addToWishlistCount")
        private Integer addToWishlistCount;
    }
}

