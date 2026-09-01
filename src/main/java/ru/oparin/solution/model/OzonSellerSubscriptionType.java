package ru.oparin.solution.model;

/**
 * Тип подписки Ozon Seller ({@code subscription.type} из {@code POST /v1/seller/info}).
 */
public enum OzonSellerSubscriptionType {

    UNKNOWN,
    /** seller/info: is_premium=true, но нет канонического type_ — Premium в ЛК подтвердить нельзя. */
    INCONCLUSIVE,
    UNSPECIFIED,
    PREMIUM_LITE,
    PREMIUM,
    PREMIUM_PLUS,
    PREMIUM_PRO;

    /**
     * Парсит значение из Ozon API.
     */
    public static OzonSellerSubscriptionType fromApiValue(String raw) {
        if (raw == null || raw.isBlank()) {
            return UNKNOWN;
        }
        String normalized = raw.trim().toUpperCase();
        for (OzonSellerSubscriptionType type : values()) {
            if (type.name().equals(normalized)) {
                return type;
            }
        }
        return UNKNOWN;
    }

    /**
     * Русское название для админки.
     */
    public String getDisplayNameRu() {
        return switch (this) {
            case INCONCLUSIVE -> "Не определено";
            case UNSPECIFIED -> "Без Premium";
            case PREMIUM_LITE -> "Premium Lite";
            case PREMIUM -> "Premium";
            case PREMIUM_PLUS -> "Premium Plus";
            case PREMIUM_PRO -> "Premium Pro";
            case UNKNOWN -> "Неизвестно";
        };
    }

    /**
     * Доступна ли воронка (переходы, корзина, конверсии) по тарифу Ozon.
     */
    public boolean supportsFunnelAnalytics() {
        return this == PREMIUM_PLUS || this == PREMIUM_PRO;
    }
}
