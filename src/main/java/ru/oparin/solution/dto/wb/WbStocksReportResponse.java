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
 * DTO для ответа с остатками товаров от /api/v2/stocks-report/products/products.
 * Остатки со складов WB.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class WbStocksReportResponse {

    @JsonProperty("data")
    private Data data;

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Data {
        @JsonProperty("items")
        private List<TableProductItem> items;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TableProductItem {
        /**
         * Артикул WB.
         */
        @JsonProperty("nmID")
        private Long nmID;

        /**
         * Является ли товар удалённым.
         */
        @JsonProperty("isDeleted")
        private Boolean isDeleted;

        /**
         * Название предмета.
         */
        @JsonProperty("subjectName")
        private String subjectName;

        /**
         * Название товара.
         */
        @JsonProperty("name")
        private String name;

        /**
         * Артикул продавца.
         */
        @JsonProperty("vendorCode")
        private String vendorCode;

        /**
         * Бренд.
         */
        @JsonProperty("brandName")
        private String brandName;

        /**
         * Ссылка на главное фото.
         */
        @JsonProperty("mainPhoto")
        private String mainPhoto;

        /**
         * Является ли товар размерным.
         */
        @JsonProperty("hasSizes")
        private Boolean hasSizes;

        /**
         * Метрики товара.
         */
        @JsonProperty("metrics")
        private Metrics metrics;

        /**
         * Доступность товара.
         */
        @JsonProperty("availability")
        private String availability;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Metrics {
        /**
         * Заказы, шт.
         */
        @JsonProperty("ordersCount")
        private Long ordersCount;

        /**
         * Заказы, сумма.
         */
        @JsonProperty("ordersSum")
        private Long ordersSum;

        /**
         * Среднее количество заказов в день.
         */
        @JsonProperty("avgOrders")
        private Double avgOrders;

        /**
         * Среднее количество заказов по месяцам.
         */
        @JsonProperty("avgOrdersByMonth")
        private List<FloatGraphByPeriodItem> avgOrdersByMonth;

        /**
         * Выкупы, шт.
         */
        @JsonProperty("buyoutCount")
        private Long buyoutCount;

        /**
         * Выкупы, сумма.
         */
        @JsonProperty("buyoutSum")
        private Long buyoutSum;

        /**
         * Процент выкупа.
         */
        @JsonProperty("buyoutPercent")
        private Integer buyoutPercent;

        /**
         * Остатки на текущий день, шт.
         */
        @JsonProperty("stockCount")
        private Long stockCount;

        /**
         * Стоимость остатков на текущий день.
         */
        @JsonProperty("stockSum")
        private Long stockSum;

        /**
         * Оборачиваемость текущих остатков.
         */
        @JsonProperty("saleRate")
        private TimePeriod saleRate;

        /**
         * Оборачиваемость средних остатков.
         */
        @JsonProperty("avgStockTurnover")
        private TimePeriod avgStockTurnover;

        /**
         * Количество дней.
         */
        @JsonProperty("days")
        private Integer days;

        /**
         * Количество часов.
         */
        @JsonProperty("hours")
        private Integer hours;

        /**
         * В пути к клиенту, шт.
         */
        @JsonProperty("toClientCount")
        private Long toClientCount;

        /**
         * В пути от клиента, шт.
         */
        @JsonProperty("fromClientCount")
        private Long fromClientCount;

        /**
         * Время отсутствия товара на складе.
         */
        @JsonProperty("officeMissingTime")
        private TimePeriod officeMissingTime;

        /**
         * Упущенные заказы, шт.
         */
        @JsonProperty("lostOrdersCount")
        private Double lostOrdersCount;

        /**
         * Упущенные заказы, сумма.
         */
        @JsonProperty("lostOrdersSum")
        private Double lostOrdersSum;

        /**
         * Упущенные выкупы, шт.
         */
        @JsonProperty("lostBuyoutsCount")
        private Double lostBuyoutsCount;

        /**
         * Упущенные выкупы, сумма.
         */
        @JsonProperty("lostBuyoutsSum")
        private Double lostBuyoutsSum;

        /**
         * Текущая цена.
         */
        @JsonProperty("currentPrice")
        private CurrentPrice currentPrice;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TimePeriod {
        /**
         * Количество дней.
         */
        @JsonProperty("days")
        private Integer days;

        /**
         * Количество часов.
         * Особые случаи:
         * -1 — бесконечная длительность
         * -2 — нулевая длительность
         * -3 — нерассчитанная длительность
         * -4 — отсутствие в течение всего периода (только для officeMissingTime)
         */
        @JsonProperty("hours")
        private Integer hours;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class FloatGraphByPeriodItem {
        @JsonProperty("period")
        private String period;

        @JsonProperty("value")
        private Double value;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CurrentPrice {
        @JsonProperty("value")
        private Double value;

        @JsonProperty("currency")
        private String currency;
    }
}
