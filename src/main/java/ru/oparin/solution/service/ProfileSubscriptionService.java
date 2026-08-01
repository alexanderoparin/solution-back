package ru.oparin.solution.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.oparin.solution.dto.ProfileSubscriptionSummaryDto;
import ru.oparin.solution.model.PlanCodes;
import ru.oparin.solution.model.User;

/**
 * Сводка подписки для профиля пользователя (тарифы теперь на уровне кабинета).
 */
@Service
@RequiredArgsConstructor
public class ProfileSubscriptionService {

    private static final String FREE_HINT =
            "Тарифы и услуги подключаются отдельно для каждого кабинета. "
                    + "В бесплатный тариф входят разделы: Товары, Сводная и Рекламные кампании.";

    /**
     * Краткая сводка: детали смотрите на странице подписки выбранного кабинета.
     */
    @Transactional(readOnly = true)
    public ProfileSubscriptionSummaryDto buildSummary(User user) {
        return ProfileSubscriptionSummaryDto.builder()
                .planName("По кабинетам")
                .planCode(PlanCodes.ANALYTICS_FREE)
                .statusLabel("См. подписку кабинета")
                .active(true)
                .autoRenew(false)
                .freePlanHint(FREE_HINT)
                .build();
    }

    /**
     * FREE создаётся при создании кабинета ({@link CabinetBillingService#initializeCabinetBilling}).
     */
    @Transactional
    public void createFreeAnalyticsSubscription(User user) {
        // no-op: биллинг привязан к кабинету
    }
}
