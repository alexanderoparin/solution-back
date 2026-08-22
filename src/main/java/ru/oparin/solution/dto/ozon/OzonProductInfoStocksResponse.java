package ru.oparin.solution.dto.ozon;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * Ответ Ozon Seller API {@code POST /v4/product/info/stocks}.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class OzonProductInfoStocksResponse {

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

        private List<Stock> stocks;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Stock {

        private Integer present;
        private Integer reserved;
        private Long sku;
        /** Тип склада: fbo, fbs и т.д. */
        private String type;
    }
}
