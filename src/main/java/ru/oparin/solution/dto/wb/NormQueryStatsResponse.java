package ru.oparin.solution.dto.wb;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.math.BigDecimal;
import java.util.List;

/**
 * DTO ответа статистики по поисковым кластерам с детализацией по дням.
 * <p>
 * Эндпоинт: POST {@code /adv/v1/normquery/stats}
 * <br>
 * Документация: promotion API, {@code V1GetNormQueryStatsResponse}.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NormQueryStatsResponse {

    /**
     * Статистика по каждой паре advertId + nmId из запроса.
     */
    private List<ResponseItem> items;

    /**
     * Блок статистики для одной пары кампания + артикул.
     */
    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ResponseItem {

        /**
         * ID рекламной кампании.
         */
        @JsonProperty("advertId")
        private Long advertId;

        /**
         * Артикул WB.
         */
        @JsonProperty("nmId")
        private Long nmId;

        /**
         * Дневная разбивка по кластерам.
         */
        private List<DailyStat> dailyStats;
    }

    /**
     * Статистика за один календарный день.
     */
    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DailyStat {

        /**
         * Дата (формат: {@code yyyy-MM-dd}).
         */
        private String date;

        /**
         * Метрики по одному поисковому кластеру за этот день.
         */
        private Stat stat;
    }

    /**
     * Метрики поискового кластера за день (поле {@code stat} в {@link DailyStat}).
     */
    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Stat {

        /**
         * Поисковый кластер (нормализованная фраза).
         */
        private String normQuery;

        /**
         * Просмотры; для CPC-кампаний может быть {@code null}.
         */
        private Integer views;

        /**
         * Клики.
         */
        private Integer clicks;

        /**
         * Добавления в корзину.
         */
        private Integer atbs;

        /**
         * Заказы.
         */
        private Integer orders;

        /**
         * CTR, %; для CPC-кампаний может быть {@code null}.
         */
        private BigDecimal ctr;

        /**
         * Средняя стоимость клика, руб.
         */
        private BigDecimal cpc;

        /**
         * CPM, руб.; для CPC-кампаний может быть {@code null}.
         */
        private BigDecimal cpm;

        /**
         * Средняя позиция в поисковой выдаче.
         */
        private BigDecimal avgPos;

        /**
         * Заказано товаров, шт.
         */
        private Integer shks;

        /**
         * Затраты на продвижение в кластере, руб.
         */
        private BigDecimal spend;
    }
}
