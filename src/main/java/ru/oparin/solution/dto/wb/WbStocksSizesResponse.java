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
 * DTO для ответа с остатками по размерам от /api/v2/stocks-report/products/sizes.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class WbStocksSizesResponse {

    @JsonProperty("data")
    private Data data;

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Data {
        /**
         * Массив размеров с остатками.
         */
        @JsonProperty("sizes")
        private List<SizeItem> sizes;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SizeItem {
        /**
         * Название размера (например, "M", "S").
         */
        @JsonProperty("name")
        private String name;

        /**
         * ID характеристики (chrtID).
         */
        @JsonProperty("chrtID")
        private Long chrtID;

        /**
         * Детализация по складам (если includeOffice = true).
         */
        @JsonProperty("offices")
        private List<OfficeStock> offices;

        /**
         * Метрики по размеру (агрегированные).
         */
        @JsonProperty("metrics")
        private SizeMetrics metrics;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class OfficeStock {
        /**
         * Название региона.
         */
        @JsonProperty("regionName")
        private String regionName;

        /**
         * ID склада WB.
         */
        @JsonProperty("officeID")
        private Long officeID;

        /**
         * Название склада WB.
         */
        @JsonProperty("officeName")
        private String officeName;

        /**
         * Метрики по складу для этого размера.
         */
        @JsonProperty("metrics")
        private OfficeMetrics metrics;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class OfficeMetrics {
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
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SizeMetrics {
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
    public static class CurrentPrice {
        @JsonProperty("minPrice")
        private Long minPrice;

        @JsonProperty("maxPrice")
        private Long maxPrice;
    }
}
