package ru.oparin.solution.dto.wb;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * DTO для запроса остатков товаров через /api/v2/stocks-report/products/products.
 * Получает остатки со складов WB (не со складов продавца).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WbStocksReportRequest {

    /**
     * Список артикулов WB для фильтрации.
     */
    @JsonProperty("nmIDs")
    private List<Long> nmIDs;

    /**
     * ID предмета.
     */
    @JsonProperty("subjectID")
    private Integer subjectID;

    /**
     * Бренд.
     */
    @JsonProperty("brandName")
    private String brandName;

    /**
     * ID ярлыка.
     */
    @JsonProperty("tagID")
    private Long tagID;

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
     * Скрыть удалённые товары (обязательное поле).
     */
    @NotNull(message = "skipDeletedNm обязателен")
    @JsonProperty("skipDeletedNm")
    private Boolean skipDeletedNm;

    /**
     * Вид сортировки данных (обязательное поле).
     */
    @NotNull(message = "orderBy обязателен")
    @JsonProperty("orderBy")
    private OrderBy orderBy;

    /**
     * Фильтр по типам доступности товаров (обязательное поле).
     * Передаем все типы для получения всех остатков.
     */
    @NotEmpty(message = "availabilityFilters не может быть пустым")
    @JsonProperty("availabilityFilters")
    private List<String> availabilityFilters;

    /**
     * Количество товаров в ответе (по умолчанию 100, максимум 1000).
     */
    @JsonProperty("limit")
    private Integer limit;

    /**
     * После какого элемента выдавать данные (обязательное поле).
     */
    @NotNull(message = "offset обязателен")
    @JsonProperty("offset")
    private Integer offset;

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
         * Направление сортировки (asc/desc).
         */
        @JsonProperty("order")
        private String order;
    }
}

