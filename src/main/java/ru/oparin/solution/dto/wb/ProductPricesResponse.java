package ru.oparin.solution.dto.wb;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * DTO для ответа с ценами товаров от /api/v2/list/goods/filter.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ProductPricesResponse {

    @JsonProperty("data")
    private Data data;

    @JsonProperty("error")
    private Boolean error;

    @JsonProperty("errorText")
    private String errorText;

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Data {
        @JsonProperty("listGoods")
        private List<Good> listGoods;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Good {
        @JsonProperty("nmID")
        private Long nmId;

        @JsonProperty("vendorCode")
        private String vendorCode;

        @JsonProperty("sizes")
        private List<Size> sizes;

        @JsonProperty("currencyIsoCode4217")
        private String currencyIsoCode4217;

        @JsonProperty("discount")
        private Integer discount;

        @JsonProperty("clubDiscount")
        private Integer clubDiscount;

        @JsonProperty("editableSizePrice")
        private Boolean editableSizePrice;

        @JsonProperty("isBadTurnover")
        private Boolean isBadTurnover;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Size {
        @JsonProperty("sizeID")
        private Long sizeId;

        @JsonProperty("price")
        private Long price;

        @JsonProperty("discountedPrice")
        private Long discountedPrice;

        @JsonProperty("clubDiscountedPrice")
        private Long clubDiscountedPrice;

        @JsonProperty("techSizeName")
        private String techSizeName;
    }
}

