package ru.oparin.solution.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.oparin.solution.dto.ProfileSubscriptionSummaryDto;
import ru.oparin.solution.model.Plan;
import ru.oparin.solution.model.PlanCodes;
import ru.oparin.solution.model.Subscription;
import ru.oparin.solution.model.User;
import ru.oparin.solution.repository.PlanRepository;
import ru.oparin.solution.repository.SubscriptionRepository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Сводка подписки для профиля пользователя.
 */
@Service
@RequiredArgsConstructor
public class ProfileSubscriptionService {

    private static final List<String> ACTIVE_STATUSES = List.of("active", "trial");
    private static final String FREE_HINT =
            "В бесплатный тариф входят разделы: Товары, Сводная и Рекламные кампании. "
                    + "Модуль «Управление РК» подключается отдельно.";

    private final SubscriptionRepository subscriptionRepository;
    private final PlanRepository planRepository;

    @Transactional(readOnly = true)
    public ProfileSubscriptionSummaryDto buildSummary(User user) {
        LocalDateTime now = LocalDateTime.now();
        Subscription subscription = subscriptionRepository
                .findFirstActiveByUserId(user.getId(), ACTIVE_STATUSES, now)
                .orElse(null);
        if (subscription == null) {
            return ProfileSubscriptionSummaryDto.builder()
                    .planName("Бесплатный доступ")
                    .planCode(PlanCodes.ANALYTICS_FREE)
                    .statusLabel("Не активна")
                    .active(false)
                    .autoRenew(false)
                    .freePlanHint(FREE_HINT)
                    .build();
        }
        Plan plan = subscription.getPlan();
        String planCode = plan != null ? plan.getCode() : null;
        boolean isFree = PlanCodes.ANALYTICS_FREE.equals(planCode);
        boolean autoRenew = Boolean.TRUE.equals(subscription.getAutoRenew());
        return ProfileSubscriptionSummaryDto.builder()
                .planName(plan != null ? plan.getName() : "Подписка")
                .planCode(planCode)
                .statusLabel("Активна")
                .active(true)
                .expiresAt(subscription.getExpiresAt())
                .nextBillingAt(isFree ? null : subscription.getExpiresAt())
                .autoRenew(isFree || autoRenew)
                .freePlanHint(isFree ? FREE_HINT : null)
                .build();
    }

    @Transactional
    public void createFreeAnalyticsSubscription(User user) {
        Plan plan = planRepository.findByCode(PlanCodes.ANALYTICS_FREE)
                .orElseThrow(() -> new IllegalStateException("План analytics_free не найден"));
        LocalDateTime now = LocalDateTime.now();
        subscriptionRepository.save(Subscription.builder()
                .user(user)
                .plan(plan)
                .status("active")
                .startedAt(now)
                .expiresAt(null)
                .autoRenew(true)
                .createdAt(now)
                .updatedAt(now)
                .build());
    }
}
