package ru.oparin.solution.dto.ozon;

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
         * Канонический тип подписки в protobuf-JSON Ozon ({@code type_}).
         */
        @JsonProperty("type_")
        private String typeUnderscore;

        /**
         * Legacy-поле {@code type}; может приходить вместе с {@code type_} и не отражать реальный тариф.
         */
        @JsonProperty("type")
        private String typeLegacy;

        /**
         * Эффективный тип: при наличии {@code type_} legacy {@code type} игнорируется.
         */
        public String resolveTypeRaw() {
            if (typeUnderscore != null && !typeUnderscore.isBlank()) {
                return typeUnderscore;
            }
            return typeLegacy;
        }
    }

    /**
     * Определяет тариф по блоку subscription из seller/info.
     * {@code is_premium} без платного {@code type_}/{@code type} не считается подпиской.
     */
    public static OzonSellerSubscriptionType resolveSubscriptionType(Subscription subscription) {
        if (subscription == null) {
            return OzonSellerSubscriptionType.UNKNOWN;
        }
        OzonSellerSubscriptionType type =
                OzonSellerSubscriptionType.fromApiValue(subscription.resolveTypeRaw());
        if (type == OzonSellerSubscriptionType.UNSPECIFIED || type == OzonSellerSubscriptionType.UNKNOWN) {
            return OzonSellerSubscriptionType.UNSPECIFIED;
        }
        if (Boolean.FALSE.equals(subscription.getPremium())) {
            return OzonSellerSubscriptionType.UNSPECIFIED;
        }
        return type;
    }

    /**
     * Консервативное автоопределение: legacy {@code type: PREMIUM} без {@code type_} не считается Premium в ЛК.
     * Ozon отдаёт одинаковый блок subscription всем кабинетам.
     */
    public static OzonSellerSubscriptionType resolveDetectedSubscriptionType(Subscription subscription) {
        if (subscription == null) {
            return OzonSellerSubscriptionType.UNKNOWN;
        }
        if (Boolean.FALSE.equals(subscription.getPremium())) {
            return OzonSellerSubscriptionType.UNSPECIFIED;
        }
        if (subscription.getTypeUnderscore() != null && !subscription.getTypeUnderscore().isBlank()) {
            return resolveSubscriptionType(subscription);
        }
        if (Boolean.TRUE.equals(subscription.getPremium())) {
            return OzonSellerSubscriptionType.INCONCLUSIVE;
        }
        return OzonSellerSubscriptionType.UNSPECIFIED;
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
