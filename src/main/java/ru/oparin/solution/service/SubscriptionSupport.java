package ru.oparin.solution.service;

import ru.oparin.solution.model.Subscription;

import java.time.LocalDateTime;
import java.util.Collection;

/**
 * Проверки активности подписки с учётом {@code expires_at = NULL} как бессрочной.
 */
public final class SubscriptionSupport {

    private SubscriptionSupport() {
    }

    public static boolean isPerpetual(Subscription subscription) {
        return subscription != null && subscription.getExpiresAt() == null;
    }

    public static boolean isActive(Subscription subscription, LocalDateTime now, Collection<String> activeStatuses) {
        if (subscription == null || now == null || activeStatuses == null) {
            return false;
        }
        if (!activeStatuses.contains(subscription.getStatus())) {
            return false;
        }
        LocalDateTime expiresAt = subscription.getExpiresAt();
        return expiresAt == null || expiresAt.isAfter(now);
    }

    public static boolean hasFutureExpiry(Subscription subscription, LocalDateTime now) {
        if (subscription == null || now == null) {
            return false;
        }
        LocalDateTime expiresAt = subscription.getExpiresAt();
        return expiresAt != null && expiresAt.isAfter(now);
    }
}
