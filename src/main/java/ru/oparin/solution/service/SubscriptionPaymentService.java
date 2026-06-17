package ru.oparin.solution.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.oparin.solution.config.SubscriptionProperties;
import ru.oparin.solution.config.TochkaProperties;
import ru.oparin.solution.dto.ActivatePlanResponse;
import ru.oparin.solution.dto.InitiatePaymentResponse;
import ru.oparin.solution.dto.PaymentStatusResponse;
import ru.oparin.solution.exception.UserException;
import ru.oparin.solution.model.*;
import ru.oparin.solution.repository.PaymentRepository;
import ru.oparin.solution.repository.PlanRepository;
import ru.oparin.solution.repository.SubscriptionRepository;
import ru.oparin.solution.service.tochka.TochkaAcquiringService;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * Сервис активации подписок и оплаты через Точка Банк.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SubscriptionPaymentService {

    private static final List<String> ACTIVE_STATUSES = List.of("active", "trial");
    private static final Set<String> TOCHKA_SUCCESS_STATUSES = Set.of("APPROVED");
    private static final Set<String> TOCHKA_FAILED_STATUSES = Set.of(
            "EXPIRED", "REFUNDED", "REFUNDED_PARTIALLY", "ON-REFUND"
    );

    private final PlanRepository planRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final PaymentRepository paymentRepository;
    private final SubscriptionProperties subscriptionProperties;
    private final TochkaProperties tochkaProperties;
    private final TochkaAcquiringService tochkaAcquiringService;
    private final ObjectMapper objectMapper;

    @Value("${app.brand-name:Click-I}")
    private String brandName;

    /**
     * Активирует бесплатный план без платёжной системы.
     */
    @Transactional
    public ActivatePlanResponse activateFreePlan(User user, Long planId) {
        if (!subscriptionProperties.isCampaignManagementEnabled()) {
            throw new UserException("Подписка на Управление РК отключена", HttpStatus.BAD_REQUEST);
        }
        Plan plan = loadCampaignManagePlan(planId);
        if (plan.getPriceRub().compareTo(BigDecimal.ZERO) > 0) {
            throw new UserException("План требует оплаты", HttpStatus.BAD_REQUEST);
        }
        if (PlanCodes.CAMPAIGN_FREE.equals(plan.getCode())
                && subscriptionRepository.existsByUser_IdAndPlan_Code(user.getId(), PlanCodes.CAMPAIGN_FREE)) {
            throw new UserException("Бесплатный период уже был активирован", HttpStatus.BAD_REQUEST);
        }

        Subscription subscription = createOrExtendSubscription(user, plan);
        log.info("Активирован бесплатный план {} для пользователя id={}", plan.getCode(), user.getId());
        return ActivatePlanResponse.builder()
                .subscriptionId(subscription.getId())
                .expiresAt(subscription.getExpiresAt())
                .build();
    }

    /**
     * Создаёт платёж и платёжную ссылку в Точка Банк.
     */
    @Transactional
    public InitiatePaymentResponse initiatePaidPlan(User user, Long planId) {
        validateSellerCanPay(user);
        if (!tochkaProperties.isConfiguredForPayments()) {
            throw new UserException("Оплата временно недоступна", HttpStatus.SERVICE_UNAVAILABLE);
        }

        Plan plan = loadCampaignManagePlan(planId);
        if (plan.getPriceRub().compareTo(BigDecimal.ZERO) <= 0) {
            throw new UserException("Для бесплатного плана используйте активацию без оплаты", HttpStatus.BAD_REQUEST);
        }

        Payment payment = Payment.builder()
                .user(user)
                .planCode(plan.getCode())
                .planName(plan.getName())
                .periodDays(plan.getPeriodDays())
                .periodType(plan.getPeriodType())
                .amount(plan.getPriceRub())
                .currency("RUB")
                .status(PaymentStatus.PENDING.getDbValue())
                .description(buildPaymentDescription(plan))
                .build();
        payment = paymentRepository.save(payment);

        var tochkaResult = tochkaAcquiringService.createSubscriptionPayment(
                user, plan, payment.getId(), brandName);

        payment.setExternalId(tochkaResult.getOperationId());
        payment.setMetadata(buildPaymentMetadata(
                tochkaResult.getOperationId(),
                tochkaResult.getPaymentLink(),
                "cm-" + payment.getId(),
                tochkaResult.getStatus()
        ));
        paymentRepository.save(payment);

        log.info("Создан платёж id={} operationId={} для userId={} plan={}",
                payment.getId(), tochkaResult.getOperationId(), user.getId(), plan.getCode());

        return InitiatePaymentResponse.builder()
                .paymentId(payment.getId())
                .paymentUrl(tochkaResult.getPaymentLink())
                .build();
    }

    /**
     * Статус платежа для владельца (только чтение из БД).
     */
    @Transactional(readOnly = true)
    public PaymentStatusResponse getPaymentStatus(User user, Long paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new UserException("Платёж не найден", HttpStatus.NOT_FOUND));
        if (!payment.getUser().getId().equals(user.getId())) {
            throw new UserException("Нет доступа к платежу", HttpStatus.FORBIDDEN);
        }

        return PaymentStatusResponse.builder()
                .paymentId(payment.getId())
                .status(payment.getStatus())
                .expiresAt(resolveSubscriptionExpiresAt(payment))
                .build();
    }

    /**
     * Завершает платёж по operationId из webhook или polling (идемпотентно).
     */
    @Transactional
    public void completePaymentByOperationId(String operationId, String tochkaStatus) {
        if (operationId == null || operationId.isBlank()) {
            return;
        }
        Payment payment = paymentRepository
                .findByExternalId(operationId)
                .orElse(null);
        if (payment == null) {
            log.warn("Payment not found for Tochka operationId={}", operationId);
            return;
        }

        if (PaymentStatus.SUCCESS.getDbValue().equals(payment.getStatus())) {
            return;
        }

        if (tochkaStatus != null && TOCHKA_SUCCESS_STATUSES.contains(tochkaStatus)) {
            payment.setStatus(PaymentStatus.SUCCESS.getDbValue());
            payment.setPaidAt(LocalDateTime.now());
            if (!hasPlanSnapshot(payment)) {
                log.error("Plan snapshot missing for payment id={}", payment.getId());
                paymentRepository.save(payment);
                return;
            }
            Subscription subscription = createOrExtendSubscriptionFromPayment(payment);
            payment.setSubscription(subscription);
            paymentRepository.save(payment);
            log.info("Платёж id={} успешен, подписка id={} до {}",
                    payment.getId(), subscription.getId(), subscription.getExpiresAt());
            return;
        }

        if (tochkaStatus != null && TOCHKA_FAILED_STATUSES.contains(tochkaStatus)) {
            payment.setStatus(PaymentStatus.FAILED.getDbValue());
            paymentRepository.save(payment);
            log.info("Платёж id={} отмечен failed, tochkaStatus={}", payment.getId(), tochkaStatus);
        }
    }

    /**
     * Создаёт или продлевает подписку на продукт плана.
     */
    @Transactional
    public Subscription createOrExtendSubscription(User user, Plan plan) {
        LocalDateTime now = LocalDateTime.now();

        Subscription current = subscriptionRepository
                .findFirstByUser_IdAndStatusInAndExpiresAtAfterOrderByExpiresAtDesc(
                        user.getId(), ACTIVE_STATUSES, now)
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

    /**
     * Продлевает подписку по снимку тарифа из платежа (не зависит от актуальной записи plans).
     */
    @Transactional
    public Subscription createOrExtendSubscriptionFromPayment(Payment payment) {
        User user = payment.getUser();
        Plan catalogPlan = payment.getPlanCode() != null
                ? planRepository.findByCode(payment.getPlanCode()).orElse(null)
                : null;

        LocalDateTime now = LocalDateTime.now();
        Subscription current = subscriptionRepository
                .findFirstByUser_IdAndStatusInAndExpiresAtAfterOrderByExpiresAtDesc(
                        user.getId(), ACTIVE_STATUSES, now)
                .orElse(null);

        LocalDateTime periodEnd = SubscriptionPeriodUtils.addPlanPeriod(
                now, payment.getPeriodDays(), payment.getPeriodType());

        if (current != null && current.getExpiresAt().isAfter(now)) {
            LocalDateTime base = current.getExpiresAt();
            current.setExpiresAt(SubscriptionPeriodUtils.addPlanPeriod(
                    base, payment.getPeriodDays(), payment.getPeriodType()));
            current.setStatus("active");
            if (catalogPlan != null) {
                current.setPlan(catalogPlan);
            }
            return subscriptionRepository.save(current);
        }

        Subscription subscription = Subscription.builder()
                .user(user)
                .plan(catalogPlan)
                .status("active")
                .startedAt(now)
                .expiresAt(periodEnd)
                .build();
        return subscriptionRepository.save(subscription);
    }

    private boolean hasPlanSnapshot(Payment payment) {
        return payment.getPeriodDays() != null
                && payment.getPeriodType() != null;
    }

    private void validateSellerCanPay(User user) {
        if (!subscriptionProperties.isCampaignManagementEnabled()) {
            throw new UserException("Подписка на Управление РК отключена", HttpStatus.BAD_REQUEST);
        }
        if (user.getRole() != Role.SELLER) {
            throw new UserException("Оплату может инициировать только селлер", HttpStatus.FORBIDDEN);
        }
        if (!Boolean.TRUE.equals(user.getEmailConfirmed())) {
            throw new UserException("Подтвердите почту перед оплатой", HttpStatus.FORBIDDEN);
        }
    }

    private Plan loadCampaignManagePlan(Long planId) {
        Plan plan = planRepository.findById(planId)
                .orElseThrow(() -> new UserException("План не найден: " + planId, HttpStatus.NOT_FOUND));
        if (!Boolean.TRUE.equals(plan.getIsActive())) {
            throw new UserException("План недоступен", HttpStatus.BAD_REQUEST);
        }
        return plan;
    }

    private String buildPaymentDescription(Plan plan) {
        return "Подписка «Управление РК»: " + plan.getName();
    }

    private String buildPaymentMetadata(
            String operationId,
            String paymentLink,
            String paymentLinkId,
            String tochkaStatus
    ) {
        try {
            ObjectNode node = objectMapper.createObjectNode();
            node.put("operationId", operationId);
            node.put("paymentLink", paymentLink);
            node.put("paymentLinkId", paymentLinkId);
            if (tochkaStatus != null) {
                node.put("tochkaStatus", tochkaStatus);
            }
            return objectMapper.writeValueAsString(node);
        } catch (Exception e) {
            log.warn("Failed to serialize payment metadata", e);
            return null;
        }
    }

    private LocalDateTime resolveSubscriptionExpiresAt(Payment payment) {
        if (payment.getSubscription() != null) {
            return payment.getSubscription().getExpiresAt();
        }
        if (!PaymentStatus.SUCCESS.getDbValue().equals(payment.getStatus())) {
            return null;
        }
        return subscriptionRepository
                .findFirstByUser_IdAndStatusInAndExpiresAtAfterOrderByExpiresAtDesc(
                        payment.getUser().getId(),
                        ACTIVE_STATUSES,
                        LocalDateTime.now()
                )
                .map(Subscription::getExpiresAt)
                .orElse(null);
    }
}
