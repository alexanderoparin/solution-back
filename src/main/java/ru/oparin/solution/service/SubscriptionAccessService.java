package ru.oparin.solution.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
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

    /**
     * Проверяет, есть ли у пользователя доступ к продукту.
     * Клиенты агентства (owner != null) всегда имеют доступ.
     *
     * @param user пользователь
     * @return true, если доступ разрешён
     */
    public boolean hasAccess(User user) {
        if (user == null) {
            return false;
        }

        if (user.getOwner() != null) {
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
     */
    public Subscription getActiveSubscription(User user) {
        if (user == null) {
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

