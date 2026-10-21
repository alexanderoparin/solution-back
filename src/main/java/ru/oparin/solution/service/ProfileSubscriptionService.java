package ru.oparin.solution.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.oparin.solution.dto.ProfileSubscriptionSummaryDto;
import ru.oparin.solution.model.PlanCodes;
import ru.oparin.solution.model.PromoCodeRedemption;
import ru.oparin.solution.model.User;

import java.util.Optional;

/**
 * Сводка подписки для профиля пользователя (тарифы теперь на уровне кабинета).
 */
@Service
@RequiredArgsConstructor
public class ProfileSubscriptionService {

    private static final String FREE_HINT =
            "Тарифы и услуги подключаются отдельно для каждого кабинета. "
                    + "В бесплатный тариф входят разделы: Товары, Сводная и Рекламные кампании.";

    private static final String PROMO_HINT =
            "Полный доступ по промокоду: все разделы сервиса, Управление РК и А/Б тесты без ограничений.";

    private static final String TRIAL_HINT =
            "Пробный полный доступ: все разделы сервиса, Управление РК и А/Б тесты без ограничений.";

    private final PromoCodeService promoCodeService;

    /**
     * Краткая сводка для профиля: активный промокод / триал, иначе тарифы смотрите у кабинета.
     * Бесплатный доступ создаётся вместе с кабинетом, не на пользователе.
     */
    @Transactional(readOnly = true)
    public ProfileSubscriptionSummaryDto buildSummary(User user) {
        Optional<PromoCodeRedemption> promo = user != null && user.getId() != null
                ? promoCodeService.findActiveRedemption(user.getId())
                : Optional.empty();
        if (promo.isPresent()) {
            String code = promo.get().getPromoCode().getCode();
            boolean trial = PlanCodes.isRegistrationTrialCode(code);
            return ProfileSubscriptionSummaryDto.builder()
                    .planName(trial ? "PRO (пробный период)" : "PRO (промокод " + code + ")")
                    .planCode(PlanCodes.PRO_MONTH)
                    .statusLabel(trial ? "Пробный период" : "Промокод активен")
                    .active(true)
                    .expiresAt(promo.get().getExpiresAt())
                    .nextBillingAt(null)
                    .autoRenew(false)
                    .freePlanHint(trial ? TRIAL_HINT : PROMO_HINT)
                    .promoCode(trial ? null : code)
                    .build();
        }
        return ProfileSubscriptionSummaryDto.builder()
                .planName("По кабинетам")
                .planCode(PlanCodes.ANALYTICS_FREE)
                .statusLabel("См. подписку кабинета")
                .active(true)
                .autoRenew(false)
                .freePlanHint(FREE_HINT)
                .promoCode(null)
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
