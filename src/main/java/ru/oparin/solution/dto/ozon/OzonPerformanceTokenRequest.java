package ru.oparin.solution.dto.ozon;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

/**
 * Тело запроса OAuth-токена Ozon Performance API.
 */
@Getter
@Builder
public class OzonPerformanceTokenRequest {

    @JsonProperty("client_id")
    private final String clientId;

    @JsonProperty("client_secret")
    private final String clientSecret;

    @JsonProperty("grant_type")
    @Builder.Default
    private final String grantType = "client_credentials";
}
