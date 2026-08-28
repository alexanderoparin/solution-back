package ru.oparin.solution.service;

import ru.oparin.solution.model.OzonSellerSubscriptionType;

/**
 * Эффективный тариф Ozon для UI: ручная настройка, probe Premium Plus или консервативный авто-режим.
 */
public final class OzonSubscriptionDisplayResolver {

    /** Сброс ручной настройки — снова автоопределение. */
    public static final String OVERRIDE_AUTO = "AUTO";

    private OzonSubscriptionDisplayResolver() {
    }

    /**
     * Вычисляет тариф для отображения в админке.
     */
    public static ResolvedOzonSubscription resolve(
            String overrideRaw,
            String detectedRaw,
            Boolean funnelAvailable,
            Boolean isPremium
    ) {
        if (isManualOverride(overrideRaw)) {
            OzonSellerSubscriptionType overrideType =
                    OzonSellerSubscriptionType.fromApiValue(overrideRaw.trim());
            if (overrideType != OzonSellerSubscriptionType.UNKNOWN) {
                return new ResolvedOzonSubscription(
                        overrideType,
                        overrideType != OzonSellerSubscriptionType.UNSPECIFIED,
                        true
                );
            }
        }

        if (Boolean.TRUE.equals(funnelAvailable)) {
            OzonSellerSubscriptionType detected = OzonSellerSubscriptionType.fromApiValue(detectedRaw);
            if (detected == OzonSellerSubscriptionType.PREMIUM_PRO) {
                return new ResolvedOzonSubscription(OzonSellerSubscriptionType.PREMIUM_PRO, true, false);
            }
            return new ResolvedOzonSubscription(OzonSellerSubscriptionType.PREMIUM_PLUS, true, false);
        }

        OzonSellerSubscriptionType detected = OzonSellerSubscriptionType.fromApiValue(detectedRaw);
        if (detected == OzonSellerSubscriptionType.UNSPECIFIED
                || detected == OzonSellerSubscriptionType.UNKNOWN) {
            return new ResolvedOzonSubscription(OzonSellerSubscriptionType.UNSPECIFIED, false, false);
        }

        return new ResolvedOzonSubscription(
                detected,
                !Boolean.FALSE.equals(isPremium),
                false
        );
    }

    /**
     * @param manual {@code true} — значение задано администратором вручную
     */
    public record ResolvedOzonSubscription(
            OzonSellerSubscriptionType type,
            boolean isPremium,
            boolean manual
    ) {
    }

    private static boolean isManualOverride(String overrideRaw) {
        return overrideRaw != null
                && !overrideRaw.isBlank()
                && !OVERRIDE_AUTO.equalsIgnoreCase(overrideRaw.trim());
    }
}
