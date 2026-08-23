package ru.oparin.solution.dto.ozon;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

/**
 * Ответ GET /api/client/statistics/{UUID} — статус async-отчёта.
 */
@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class OzonPerformanceReportStatusResponse {

    @JsonProperty("UUID")
    private String uuid;

    private String state;

    private String error;

    private String link;

    private String kind;
}
