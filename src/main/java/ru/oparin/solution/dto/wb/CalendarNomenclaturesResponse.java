package ru.oparin.solution.dto.wb;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.util.List;

/**
 * DTO ответа списка товаров для участия в акции календаря WB.
 * GET /api/v1/calendar/promotions/nomenclatures
 * Документация: https://dev.wildberries.ru/docs/openapi/promotion#tag/Kalendar-akcij
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class CalendarNomenclaturesResponse {

    @JsonProperty("data")
    private Data data;

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Data {
        @JsonProperty("nomenclatures")
        private List<CalendarNomenclatureItem> nomenclatures;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CalendarNomenclatureItem {
        /** Артикул WB (nmId). */
        @JsonProperty("id")
        private Long id;
        @JsonProperty("inAction")
        private Boolean inAction;
        @JsonProperty("price")
        private Integer price;
        @JsonProperty("currencyCode")
        private String currencyCode;
        @JsonProperty("planPrice")
        private Integer planPrice;
        @JsonProperty("discount")
        private Integer discount;
        @JsonProperty("planDiscount")
        private Integer planDiscount;
    }
}
