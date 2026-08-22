package ru.oparin.solution.dto.ozon;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * Ответ Ozon Seller API {@code POST /v5/product/info/prices}.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class OzonProductInfoPricesResponse {

    private String cursor;
    private List<Item> items;
    private Integer total;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Item {

        @JsonProperty("product_id")
        private Long productId;

        @JsonProperty("offer_id")
        private String offerId;

        private Price price;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Price {

        private BigDecimal price;

        @JsonProperty("old_price")
        private BigDecimal oldPrice;

        @JsonProperty("marketing_price")
        private BigDecimal marketingPrice;

        @JsonProperty("min_price")
        private BigDecimal minPrice;

        @JsonProperty("currency_code")
        private String currencyCode;
    }
}
