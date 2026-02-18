package ru.oparin.solution.dto.wb;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.util.List;

/**
 * DTO ответа списка акций календаря WB.
 * GET /api/v1/calendar/promotions
 * Документация: https://dev.wildberries.ru/docs/openapi/promotion#tag/Kalendar-akcij
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class CalendarPromotionsResponse {

    @JsonProperty("data")
    private Data data;

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Data {
        @JsonProperty("promotions")
        private List<CalendarPromotionItem> promotions;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CalendarPromotionItem {
        @JsonProperty("id")
        private Long id;
        @JsonProperty("name")
        private String name;
        @JsonProperty("startDateTime")
        private String startDateTime;
        @JsonProperty("endDateTime")
        private String endDateTime;
        @JsonProperty("type")
        private String type;
    }
}
