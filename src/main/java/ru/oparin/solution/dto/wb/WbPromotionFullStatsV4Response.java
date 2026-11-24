package ru.oparin.solution.dto.wb;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.math.BigDecimal;
import java.util.List;

/**
 * Ответ GET /api/advert/v4/fullstats — плоская статистика кампаний.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class WbPromotionFullStatsV4Response {

    /**
     * Строки статистики.
     */
    @JsonProperty("rows")
    private List<Row> rows;

    /**
     * Одна строка статистики (кампания + артикул + день + зона + тип конверсии).
     */
    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Row {

        @JsonProperty("advertId")
        private Long advertId;

        @JsonProperty("nmId")
        private Long nmId;

        @JsonProperty("date")
        private String date;

        @JsonProperty("placement")
        private String placement;

        @JsonProperty("conversionType")
        private String conversionType;

        @JsonProperty("currency")
        private String currency;

        @JsonProperty("views")
        private Long views;

        @JsonProperty("clicks")
        private Long clicks;

        @JsonProperty("ctr")
        private BigDecimal ctr;

        @JsonProperty("cpc")
        private BigDecimal cpc;

        @JsonProperty("sum")
        private BigDecimal sum;

        @JsonProperty("atbs")
        private Integer atbs;

        @JsonProperty("orders")
        private Integer orders;

        @JsonProperty("cr")
        private BigDecimal cr;

        @JsonProperty("shks")
        private Integer shks;

        @JsonProperty("sumPrice")
        private BigDecimal sumPrice;

        @JsonProperty("canceled")
        private Integer canceled;

        @JsonProperty("avgPos")
        private BigDecimal avgPos;
    }
}
