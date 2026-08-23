package ru.oparin.solution.dto.ozon;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * Ответ Ozon Seller API {@code POST /v3/product/list}.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class OzonProductListResponse {

    private Result result;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Result {
        private List<Item> items;
        private Integer total;

        @JsonProperty("last_id")
        private String lastId;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Item {
        @JsonProperty("product_id")
        private Long productId;

        @JsonProperty("offer_id")
        private String offerId;

        /** SKU из {@code /v3/product/list} — нужен для {@code /v1/analytics/data}. */
        private Long sku;
    }
}
