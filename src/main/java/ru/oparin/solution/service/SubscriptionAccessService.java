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
 * Сервис проверки доступа к функционалу по подписке.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SubscriptionAccessService {

    private static final List<String> ACTIVE_STATUSES = List.of("active", "trial");

    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionProperties subscriptionProperties;

    /**
     * Проверяет, есть ли у пользователя доступ к продукту.
     * ADMIN и MANAGER всегда имеют доступ. Клиенты агентства (owner != null) всегда имеют доступ.
     * Обычные селлеры (не из агентства) должны подтвердить почту — иначе доступа нет.
     * При выключенной оплате (billingEnabled=false) селлеры без владельца имеют доступ без проверки подписки.
     *
     * @param user пользователь
     * @return true, если доступ разрешён
     */
    public boolean hasAccess(User user) {
        if (user == null) {
            return false;
        }

        if (user.getRole() == Role.ADMIN || user.getRole() == Role.MANAGER) {
            return true;
        }

        if (user.getOwner() != null) {
            return true;
        }

        if (user.getRole() == Role.SELLER && !Boolean.TRUE.equals(user.getEmailConfirmed())) {
            return false;
        }

        if (!subscriptionProperties.isBillingEnabled()) {
            return true;
        }

        LocalDateTime now = LocalDateTime.now();
        return subscriptionRepository
                .findFirstByUser_IdAndStatusInAndExpiresAtAfterOrderByExpiresAtDesc(
                        user.getId(),
                        ACTIVE_STATUSES,
                        now
                )
                .isPresent();
    }

    /**
     * Возвращает активную подписку пользователя, если она есть.
     * При выключенной оплате возвращает null (фронту не нужны даты подписки).
     */
    public Subscription getActiveSubscription(User user) {
        if (user == null) {
            return null;
        }
        if (!subscriptionProperties.isBillingEnabled()) {
            return null;
        }
        LocalDateTime now = LocalDateTime.now();
        return subscriptionRepository
                .findFirstByUser_IdAndStatusInAndExpiresAtAfterOrderByExpiresAtDesc(
                        user.getId(),
                        ACTIVE_STATUSES,
                        now
                )
                .orElse(null);
    }
}

