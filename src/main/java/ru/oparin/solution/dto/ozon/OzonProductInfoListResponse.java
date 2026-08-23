package ru.oparin.solution.dto.ozon;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
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
        @JsonDeserialize(using = OzonStringOrStringArrayDeserializer.class)
        private String primaryImage;

        private List<String> images;

        /** Источники товара; SKU для аналитики часто только здесь. */
        private List<Source> sources;

        /**
         * Возвращает SKU: top-level поле или первый непустой из {@link #sources}.
         */
        public Long resolveSku() {
            if (sku != null) {
                return sku;
            }
            if (sources == null || sources.isEmpty()) {
                return null;
            }
            for (Source source : sources) {
                if (source != null && source.getSku() != null) {
                    return source.getSku();
                }
            }
            return null;
        }
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Source {
        private Long sku;
        private String source;
    }
}
