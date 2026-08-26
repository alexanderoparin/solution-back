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

    private Subscription subscription;

    /**
     * Подписка из ответа (если не вложена в {@link #result}).
     */
    public Subscription resolveSubscription() {
        if (result != null && result.getSubscription() != null) {
            return result.getSubscription();
        }
        return subscription;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Subscription {

        @JsonProperty("is_premium")
        private Boolean premium;

        private String type;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Result {

        @JsonProperty("company_id")
        private Long companyId;

        private String name;

        @JsonProperty("is_enabled")
        private Boolean enabled;

        private Subscription subscription;
    }
}
