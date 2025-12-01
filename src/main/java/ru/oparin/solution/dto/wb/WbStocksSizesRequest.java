package ru.oparin.solution.dto.wb;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * DTO для запроса остатков по размерам через /api/v2/stocks-report/products/sizes.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WbStocksSizesRequest {

    /**
     * Артикул WB (обязательное поле).
     */
    @NotNull(message = "nmID обязателен")
    @JsonProperty("nmID")
    private Long nmID;

    /**
     * Период (обязательное поле).
     */
    @NotNull(message = "currentPeriod обязателен")
    @JsonProperty("currentPeriod")
    private Period currentPeriod;

    /**
     * Тип складов хранения товаров (обязательное поле):
     * "" — все
     * "wb" — Склады WB
     * "mp" — Склады продавца
     */
    @NotNull(message = "stockType обязателен")
    @JsonProperty("stockType")
    private String stockType;

    /**
     * Вид сортировки данных (обязательное поле).
     */
    @NotNull(message = "orderBy обязателен")
    @JsonProperty("orderBy")
    private OrderBy orderBy;

    /**
     * Включить детализацию по складам (обязательное поле).
     */
    @NotNull(message = "includeOffice обязателен")
    @JsonProperty("includeOffice")
    private Boolean includeOffice;

    /**
     * Период с датами начала и окончания.
     */
    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Period {
        /**
         * Начало периода (дата в формате YYYY-MM-DD).
         */
        @NotNull(message = "start обязателен")
        @JsonProperty("start")
        private String start;

        /**
         * Конец периода (дата в формате YYYY-MM-DD).
         */
        @NotNull(message = "end обязателен")
        @JsonProperty("end")
        private String end;
    }

    /**
     * Вид сортировки данных.
     */
    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderBy {
        /**
         * Поле для сортировки.
         */
        @JsonProperty("field")
        private String field;

        /**
         * Режим сортировки (asc/desc).
         */
        @JsonProperty("mode")
        private String mode;
    }
}

