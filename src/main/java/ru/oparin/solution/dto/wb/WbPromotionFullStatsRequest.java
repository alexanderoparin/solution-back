package ru.oparin.solution.dto.wb;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.util.List;

/**
 * DTO для запроса статистики кампаний.
 * Эндпоинт: GET /api/advert/v4/fullstats (query: advertIds, beginDate, endDate).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WbPromotionFullStatsRequest {

    /**
     * Список ID кампаний.
     * От 1 до 50 элементов.
     */
    @NotEmpty(message = "advertId не может быть пустым")
    @Size(min = 1, max = 50, message = "advertId должен содержать от 1 до 50 элементов")
    @JsonProperty("advertId")
    private List<Long> advertId;

    /**
     * Дата начала периода (формат: YYYY-MM-DD).
     */
    @NotNull(message = "dateFrom обязателен")
    @JsonProperty("dateFrom")
    private String dateFrom;

    /**
     * Дата окончания периода (формат: YYYY-MM-DD).
     */
    @NotNull(message = "dateTo обязателен")
    @JsonProperty("dateTo")
    private String dateTo;
}

