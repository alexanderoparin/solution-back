package ru.oparin.solution.dto.ozon;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * Ответ Ozon Seller API {@code POST /v1/seller/info}.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class OzonSellerInfoResponse {

    private Result result;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Result {

        @JsonProperty("company_id")
        private Long companyId;

        private String name;

        @JsonProperty("is_enabled")
        private Boolean enabled;
    }
}
