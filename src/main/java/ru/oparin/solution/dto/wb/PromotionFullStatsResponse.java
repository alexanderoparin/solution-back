package ru.oparin.solution.dto.wb;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

/**
 * DTO для ответа статистики кампаний.
 * Эндпоинт: GET /adv/v3/fullstats
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class PromotionFullStatsResponse {

    /**
     * Список статистики по кампаниям.
     */
    @JsonProperty("adverts")
    private List<CampaignStats> adverts;

    /**
     * Статистика по кампании.
     */
    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CampaignStats {
        /**
         * ID кампании.
         */
        @JsonProperty("advertId")
        private Long advertId;

        /**
         * Массив статистики по дням.
         */
        @JsonProperty("days")
        private List<DayStats> days;

        /**
         * Агрегированные метрики за весь период.
         */
        @JsonProperty("views")
        private Integer views;

        @JsonProperty("clicks")
        private Integer clicks;

        @JsonProperty("ctr")
        private BigDecimal ctr;

        @JsonProperty("sum")
        private BigDecimal sum;

        @JsonProperty("orders")
        private Integer orders;

        @JsonProperty("cr")
        private BigDecimal cr;

        @JsonProperty("cpc")
        private BigDecimal cpc;

        @JsonProperty("atbs")
        private Integer atbs;

        @JsonProperty("canceled")
        private Integer canceled;

        @JsonProperty("shks")
        private Integer shks;

        @JsonProperty("sum_price")
        private BigDecimal sumPrice;

        /**
         * Статистика за день.
         */
        @Getter
        @Setter
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class DayStats {
            /**
             * Дата статистики (формат: ISO 8601, например "2025-11-23T00:00:00Z").
             */
            @JsonProperty("date")
            private String date;

            /**
             * Показы.
             */
            @JsonProperty("views")
            private Integer views;

            /**
             * Клики.
             */
            @JsonProperty("clicks")
            private Integer clicks;

            /**
             * CTR (Click-Through Rate) - процент кликов от показов.
             */
            @JsonProperty("ctr")
            private BigDecimal ctr;

            /**
             * Расходы (в рублях).
             */
            @JsonProperty("sum")
            private BigDecimal sum;

            /**
             * Заказы.
             */
            @JsonProperty("orders")
            private Integer orders;

            /**
             * CR (Conversion Rate) - процент заказов от кликов.
             */
            @JsonProperty("cr")
            private BigDecimal cr;

            /**
             * CPC (Cost Per Click) - стоимость клика.
             */
            @JsonProperty("cpc")
            private BigDecimal cpc;

            /**
             * Добавлено в корзину.
             */
            @JsonProperty("atbs")
            private Integer atbs;

            /**
             * Отменено заказов.
             */
            @JsonProperty("canceled")
            private Integer canceled;

            /**
             * ШК (штрих-коды).
             */
            @JsonProperty("shks")
            private Integer shks;

            /**
             * Сумма заказов (в рублях).
             */
            @JsonProperty("sum_price")
            private BigDecimal sumPrice;
        }
    }
}
