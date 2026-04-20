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
     * ADMIN всегда имеет доступ. Клиенты агентства ({@code isAgencyClient}) — без проверки почты.
     * Остальные при неподтверждённой почте не имеют доступа.
     * MANAGER после подтверждения почты имеет доступ без подписки.
     * WORKER под селлером после подтверждения почты — доступ без своей подписки (кабинет селлера).
     * При выключенной оплате после подтверждения почты — доступ без проверки подписки (кроме сценария с подпиской ниже).
     *
     * @param user пользователь
     * @return true, если доступ разрешён
     */
    public boolean hasAccess(User user) {
        if (user == null) {
            return false;
        }

        if (user.getRole() == Role.ADMIN) {
            return true;
        }

        if (Boolean.TRUE.equals(user.getIsAgencyClient())) {
            return true;
        }

        if (!Boolean.TRUE.equals(user.getEmailConfirmed())) {
            return false;
        }

        if (user.getRole() == Role.MANAGER) {
            return true;
        }

        if (user.getRole() == Role.WORKER && user.getOwner() != null) {
            return true;
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

