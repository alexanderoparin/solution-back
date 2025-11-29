package ru.oparin.solution.dto.wb;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.util.List;

/**
 * DTO для запроса истории аналитики воронки продаж по дням.
 * 
 * Эндпоинт: POST /api/analytics/v3/sales-funnel/products/history
 * Документация: https://dev.wildberries.ru/openapi/analytics#tag/Voronka-prodazh/operation/postSalesFunnelProductsHistory
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SaleFunnelHistoryRequest {

    /**
     * Запрашиваемый период.
     */
    @NotNull(message = "selectedPeriod обязателен")
    @Valid
    @JsonProperty("selectedPeriod")
    private Period selectedPeriod;

    /**
     * Артикулы WB, по которым нужно составить отчёт.
     * От 1 до 20 элементов.
     */
    @NotEmpty(message = "nmIds не может быть пустым")
    @Size(min = 1, max = 20, message = "nmIds должен содержать от 1 до 20 элементов")
    @JsonProperty("nmIds")
    private List<Long> nmIds;

    /**
     * Скрыть удалённые карточки товаров.
     */
    @JsonProperty("skipDeletedNm")
    private Boolean skipDeletedNm;

    /**
     * Тип агрегации. Если не указано, то по умолчанию используется агрегация по дням.
     * Доступные уровни агрегации: "day", "week"
     */
    @JsonProperty("aggregationLevel")
    private String aggregationLevel;

    /**
     * Период с датами начала и окончания.
     */
    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @ToString
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
}

