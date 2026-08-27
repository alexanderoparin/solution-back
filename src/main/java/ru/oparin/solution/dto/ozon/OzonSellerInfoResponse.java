package ru.oparin.solution.dto.ozon;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import ru.oparin.solution.model.OzonSellerSubscriptionType;

/**
 * Ответ Ozon Seller API {@code POST /v1/seller/info}.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class OzonSellerInfoResponse {

    private Result result;

    private Subscription subscription;

    /**
     * Подписка из ответа. Актуальный формат Ozon — корневой {@link #subscription};
     * legacy-обёртка {@link #result} используется как fallback.
     */
    public Subscription resolveSubscription() {
        if (subscription != null) {
            return subscription;
        }
        if (result != null && result.getSubscription() != null) {
            return result.getSubscription();
        }
        return null;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Subscription {

        @JsonProperty("is_premium")
        private Boolean premium;

        /**
         * Тип подписки. В protobuf-JSON Ozon поле называется {@code type_}, в legacy-ответах — {@code type}.
         */
        @JsonProperty("type_")
        @JsonAlias("type")
        private String type;

        /**
         * Сырое значение типа подписки из seller/info.
         */
        public String resolveTypeRaw() {
            return type;
        }
    }

    /**
     * Определяет тариф по блоку subscription из seller/info.
     */
    public static OzonSellerSubscriptionType resolveSubscriptionType(Subscription subscription) {
        if (subscription == null) {
            return OzonSellerSubscriptionType.UNKNOWN;
        }
        if (Boolean.FALSE.equals(subscription.getPremium())) {
            return OzonSellerSubscriptionType.UNSPECIFIED;
        }
        OzonSellerSubscriptionType type =
                OzonSellerSubscriptionType.fromApiValue(subscription.resolveTypeRaw());
        if (type == OzonSellerSubscriptionType.UNKNOWN && Boolean.TRUE.equals(subscription.getPremium())) {
            return OzonSellerSubscriptionType.UNKNOWN;
        }
        return type;
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
