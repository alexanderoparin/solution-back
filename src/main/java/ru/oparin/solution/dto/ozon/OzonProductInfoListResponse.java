package ru.oparin.solution.dto.ozon;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * Ответ Ozon Seller API {@code POST /v3/product/info/list}.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class OzonProductInfoListResponse {

    private List<Item> items;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Item {
        private Long id;

        private String name;

        @JsonProperty("offer_id")
        private String offerId;

        private Long sku;

        @JsonProperty("primary_image")
        private String primaryImage;

        private List<String> images;
    }
}
