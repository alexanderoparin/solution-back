package ru.oparin.solution.dto.ozon;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;

/**
 * Ответ Ozon Seller API {@code POST /v1/analytics/data}.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class OzonAnalyticsDataResponse {

    private Result result;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Result {
        private List<Row> data;
        private List<Double> totals;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Row {
        private List<Dimension> dimensions;
        private List<Double> metrics;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Dimension {
        private String id;
        private String name;
    }
}
