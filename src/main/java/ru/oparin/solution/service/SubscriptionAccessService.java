package ru.oparin.solution.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.oparin.solution.config.SubscriptionProperties;
import ru.oparin.solution.model.Role;
import ru.oparin.solution.model.Subscription;
import ru.oparin.solution.model.User;
import ru.oparin.solution.repository.SubscriptionRepository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Проверка доступа к функционалу по подписке и email.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SubscriptionAccessService {

    private static final List<String> ACTIVE_STATUSES = List.of("active", "trial");

    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionProperties subscriptionProperties;
    private final CabinetAccessService cabinetAccessService;

    public boolean hasAccess(User user) {
        if (user == null) {
            return false;
        }
        if (user.getRole() == Role.ADMIN) {
            return true;
        }
        if (!Boolean.TRUE.equals(user.getEmailConfirmed())) {
            return false;
        }
        if (!subscriptionProperties.isBillingEnabled()) {
            return true;
        }
        if (hasActiveSubscription(user)) {
            return true;
        }
        return cabinetAccessService.hasAnyCabinetAccess(user);
    }

    public Subscription getActiveSubscription(User user) {
        if (user == null || !subscriptionProperties.isBillingEnabled()) {
            return null;
        }
        LocalDateTime now = LocalDateTime.now();
        return subscriptionRepository
                .findFirstByUser_IdAndStatusInAndExpiresAtAfterOrderByExpiresAtDesc(
                        user.getId(), ACTIVE_STATUSES, now)
                .orElse(null);
    }

    private boolean hasActiveSubscription(User user) {
        return getActiveSubscription(user) != null;
    }
}
