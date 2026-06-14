package ru.oparin.solution.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.oparin.solution.config.SubscriptionProperties;
import ru.oparin.solution.dto.ActivatePlanResponse;
import ru.oparin.solution.exception.UserException;
import ru.oparin.solution.model.Plan;
import ru.oparin.solution.model.PlanProductCode;
import ru.oparin.solution.model.Subscription;
import ru.oparin.solution.model.User;
import ru.oparin.solution.repository.PlanRepository;
import ru.oparin.solution.repository.SubscriptionRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Сервис активации подписок (бесплатные планы и продление администратором).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SubscriptionPaymentService {

    private static final List<String> ACTIVE_STATUSES = List.of("active", "trial");

    private final PlanRepository planRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionProperties subscriptionProperties;

    /**
     * Активирует бесплатный план без платёжной системы.
     */
    @Transactional
    public ActivatePlanResponse activateFreePlan(User user, Long planId) {
        if (!subscriptionProperties.isCampaignManagementEnabled()) {
            throw new UserException("Подписка на Управление РК отключена", HttpStatus.BAD_REQUEST);
        }
        Plan plan = planRepository.findById(planId)
                .orElseThrow(() -> new UserException("План не найден: " + planId, HttpStatus.NOT_FOUND));
        if (!Boolean.TRUE.equals(plan.getIsActive())) {
            throw new UserException("План недоступен", HttpStatus.BAD_REQUEST);
        }
        if (!PlanProductCode.CAMPAIGN_MANAGE.equals(plan.getProductCode())) {
            throw new UserException("План не относится к Управлению РК", HttpStatus.BAD_REQUEST);
        }
        if (plan.getPriceRub().compareTo(BigDecimal.ZERO) > 0) {
            throw new UserException("План требует оплаты", HttpStatus.BAD_REQUEST);
        }
        if (PlanProductCode.CAMPAIGN_FREE.equals(plan.getCode())
                && subscriptionRepository.existsByUser_IdAndPlan_Code(user.getId(), PlanProductCode.CAMPAIGN_FREE)) {
            throw new UserException("Бесплатный период уже был активирован", HttpStatus.BAD_REQUEST);
        }

        Subscription subscription = createOrExtendSubscription(user, plan);
        log.info("Активирован бесплатный план {} для пользователя id={}", plan.getCode(), user.getId());
        return ActivatePlanResponse.builder()
                .subscriptionId(subscription.getId())
                .expiresAt(subscription.getExpiresAt())
                .build();
    }

    private Subscription createOrExtendSubscription(User user, Plan plan) {
        LocalDateTime now = LocalDateTime.now();
        String productCode = plan.getProductCode() != null ? plan.getProductCode() : PlanProductCode.LEGACY;

        Subscription current = subscriptionRepository
                .findFirstByUser_IdAndPlan_ProductCodeAndStatusInAndExpiresAtAfterOrderByExpiresAtDesc(
                        user.getId(), productCode, ACTIVE_STATUSES, now)
                .orElse(null);

        LocalDateTime base = now;
        if (current != null && current.getExpiresAt().isAfter(now)) {
            base = current.getExpiresAt();
            current.setExpiresAt(SubscriptionPeriodUtils.addPlanPeriod(base, plan));
            current.setStatus("active");
            current.setPlan(plan);
            return subscriptionRepository.save(current);
        }

        Subscription subscription = Subscription.builder()
                .user(user)
                .plan(plan)
                .status(plan.getPriceRub().compareTo(BigDecimal.ZERO) == 0 ? "trial" : "active")
                .startedAt(now)
                .expiresAt(SubscriptionPeriodUtils.addPlanPeriod(now, plan))
                .build();
        return subscriptionRepository.save(subscription);
    }
}
