package ru.oparin.solution.dto.ozon;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

/**
 * Ответ POST /api/client/statistics — UUID async-отчёта.
 */
@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class OzonPerformanceStatisticsSubmitResponse {

    @JsonProperty("UUID")
    private String uuid;

    private String vendor;
}
