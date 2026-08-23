package ru.oparin.solution.dto.ozon;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonSetter;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

/**
 * Ответ POST /v1/product/rating-by-sku — контент-рейтинг по SKU.
 */
@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class OzonProductRatingBySkuResponse {

    private List<Product> products;

    public List<Product> resolveProducts() {
        return products != null ? products : Collections.emptyList();
    }

    @Getter
    @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Product {

        private Long sku;

        /** Контент-рейтинг 0–100. */
        private BigDecimal rating;

        @JsonSetter("sku")
        public void setSkuFromJson(Object value) {
            if (value == null) {
                this.sku = null;
                return;
            }
            if (value instanceof Number number) {
                this.sku = number.longValue();
                return;
            }
            String text = String.valueOf(value).trim();
            if (text.isEmpty()) {
                this.sku = null;
                return;
            }
            try {
                this.sku = Long.parseLong(text);
            } catch (NumberFormatException e) {
                this.sku = null;
            }
        }
    }
}
