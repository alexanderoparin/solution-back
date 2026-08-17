package ru.oparin.solution.dto.wb;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.util.List;

/**
 * Ответ остатков FBS: POST /api/v3/stocks/{warehouseId}.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class WbFbsStocksResponse {

    /**
     * Остатки по размерам. Отсутствующие в запросе {@code chrtId} не возвращаются.
     */
    @JsonProperty("stocks")
    private List<Item> stocks;

    /**
     * Остаток одного размера на складе продавца.
     */
    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Item {

        /**
         * ID размера товара.
         */
        @JsonProperty("chrtId")
        private Long chrtId;

        /**
         * Остаток.
         */
        @JsonProperty("amount")
        private Integer amount;

        /**
         * Баркод (в живом ответе WB есть, в swagger может отсутствовать).
         */
        @JsonProperty("sku")
        private String sku;
    }
}
