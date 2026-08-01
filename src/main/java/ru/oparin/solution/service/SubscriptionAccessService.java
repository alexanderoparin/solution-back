package ru.oparin.solution.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.oparin.solution.config.SubscriptionProperties;
import ru.oparin.solution.model.Role;
import ru.oparin.solution.model.Subscription;
import ru.oparin.solution.model.User;
import ru.oparin.solution.repository.CabinetRepository;

/**
 * Глобальный доступ в приложение (email / billing flag).
 * Feature-gates (PRO, РК, А/Б) — на уровне кабинета.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SubscriptionAccessService {

    private final SubscriptionProperties subscriptionProperties;
    private final CabinetAccessService cabinetAccessService;
    private final CabinetRepository cabinetRepository;

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
        if (cabinetRepository.existsByUser_Id(user.getId())) {
            return true;
        }
        return cabinetAccessService.hasAnyCabinetAccess(user);
    }

    /**
     * Глобальная «активная подписка» больше не используется — тарифы на кабинете.
     */
    public Subscription getActiveSubscription(User user) {
        return null;
    }
}
