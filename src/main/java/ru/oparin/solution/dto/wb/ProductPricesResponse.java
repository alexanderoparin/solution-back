package ru.oparin.solution.dto.wb;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.math.BigDecimal;
import java.util.List;

/**
 * DTO для ответа с ценами товаров от POST /api/v2/list/goods/filter.
 * Содержит информацию о ценах, скидках и размерах товаров.
 *
 * @see <a href="https://dev.wildberries.ru/swagger/products">Swagger — WB API (Товары)</a>
 * @see <a href="https://dev.wildberries.ru/docs/openapi/work-with-products">Документация: работа с товарами</a>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ProductPricesResponse {
    /**
     * Данные ответа с информацией о товарах.
     */
    @JsonProperty("data")
    private Data data;

    /**
     * Флаг наличия ошибки в ответе.
     */
    @JsonProperty("error")
    private Boolean error;

    /**
     * Текст ошибки, если она произошла.
     */
    @JsonProperty("errorText")
    private String errorText;

    /**
     * Внутренний класс для данных ответа.
     */
    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Data {
        /**
         * Список товаров с ценами.
         */
        @JsonProperty("listGoods")
        private List<Good> listGoods;
    }

    /**
     * Внутренний класс для информации о товаре.
     */
    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Good {
        /**
         * Артикул WB (nmID).
         */
        @JsonProperty("nmID")
        private Long nmId;

        /**
         * Артикул продавца.
         */
        @JsonProperty("vendorCode")
        private String vendorCode;

        /**
         * Список размеров товара с ценами.
         */
        @JsonProperty("sizes")
        private List<Size> sizes;

        /**
         * Код валюты по ISO 4217.
         */
        @JsonProperty("currencyIsoCode4217")
        private String currencyIsoCode4217;

        /**
         * Скидка продавца в процентах.
         */
        @JsonProperty("discount")
        private Integer discount;

        /**
         * Скидка WB Клуба в процентах.
         */
        @JsonProperty("clubDiscount")
        private Integer clubDiscount;

        /**
         * Можно ли редактировать цену размера.
         */
        @JsonProperty("editableSizePrice")
        private Boolean editableSizePrice;

        /**
         * Плохой оборот товара.
         */
        @JsonProperty("isBadTurnover")
        private Boolean isBadTurnover;
    }

    /**
     * Внутренний класс для информации о размере товара.
     */
    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Size {
        /**
         * ID размера (sizeID).
         */
        @JsonProperty("sizeID")
        private Long sizeId;

        /**
         * Цена до скидки в рублях.
         */
        @JsonProperty("price")
        private BigDecimal price;

        /**
         * Цена со скидкой продавца в рублях.
         */
        @JsonProperty("discountedPrice")
        private BigDecimal discountedPrice;

        /**
         * Цена со скидкой WB Клуба в рублях.
         */
        @JsonProperty("clubDiscountedPrice")
        private BigDecimal clubDiscountedPrice;

        /**
         * Цена с СПП (Скидка постоянного покупателя) в рублях.
         * СПП - это скидка, которую дает сам Wildberries постоянным покупателям.
         */
        @JsonProperty("sppPrice")
        private BigDecimal sppPrice;

        /**
         * Название размера (например, "S", "M", "L", "42").
         */
        @JsonProperty("techSizeName")
        private String techSizeName;
    }
}

