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
import ru.oparin.solution.repository.CabinetRepository;
import ru.oparin.solution.repository.PaymentRepository;
import ru.oparin.solution.repository.PlanRepository;
import ru.oparin.solution.repository.SubscriptionRepository;
import ru.oparin.solution.service.tochka.TochkaAcquiringService;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * Активация тарифов/услуг кабинета и оплата через Точка Банк.
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
    private final CabinetRepository cabinetRepository;
    private final SubscriptionProperties subscriptionProperties;
    private final TochkaProperties tochkaProperties;
    private final TochkaAcquiringService tochkaAcquiringService;
    private final AbTestQuotaService abTestQuotaService;
    private final ObjectMapper objectMapper;

    @Value("${app.brand-name:Clicki}")
    private String brandName;

    /**
     * Активирует бесплатный план услуги «Управление РК» для кабинета.
     */
    @Transactional
    public ActivatePlanResponse activateFreePlan(User user, Long planId, Long cabinetId) {
        Cabinet cabinet = requireOwnedCabinet(user, cabinetId);
        if (!subscriptionProperties.isCampaignManagementEnabled()) {
            throw new UserException("Подписка на Управление РК отключена", HttpStatus.BAD_REQUEST);
        }
        Plan plan = loadActivePlan(planId);
        if (plan.getKind() != PlanKind.CAMPAIGN) {
            throw new UserException("Бесплатная активация доступна только для услуги «Управление РК»", HttpStatus.BAD_REQUEST);
        }
        if (plan.getPriceRub().compareTo(BigDecimal.ZERO) > 0) {
            throw new UserException("План требует оплаты", HttpStatus.BAD_REQUEST);
        }
        if (PlanCodes.CAMPAIGN_FREE.equals(plan.getCode())
                && subscriptionRepository.existsByCabinet_IdAndPlan_Code(cabinet.getId(), PlanCodes.CAMPAIGN_FREE)) {
            throw new UserException("Бесплатный период уже был активирован", HttpStatus.BAD_REQUEST);
        }

        Subscription subscription = createOrExtendKindSubscription(user, cabinet, plan);
        log.info("Активирован бесплатный план {} для cabinetId={}", plan.getCode(), cabinet.getId());
        return ActivatePlanResponse.builder()
                .subscriptionId(subscription.getId())
                .expiresAt(subscription.getExpiresAt())
                .build();
    }

    /**
     * Создаёт платёж и платёжную ссылку в Точка Банк (PRO / Управление РК / пакет А/Б).
     */
    @Transactional
    public InitiatePaymentResponse initiatePaidPlan(User user, Long planId, Long cabinetId) {
        Cabinet cabinet = requireOwnedCabinet(user, cabinetId);
        validateSellerCanPay(user);
        if (!tochkaProperties.isConfiguredForPayments()) {
            throw new UserException("Оплата временно недоступна", HttpStatus.SERVICE_UNAVAILABLE);
        }

        Plan plan = loadActivePlan(planId);
        if (plan.getPriceRub().compareTo(BigDecimal.ZERO) <= 0) {
            throw new UserException("Для бесплатного плана используйте активацию без оплаты", HttpStatus.BAD_REQUEST);
        }
        if (plan.getKind() == PlanKind.CAMPAIGN && !subscriptionProperties.isCampaignManagementEnabled()) {
            throw new UserException("Подписка на Управление РК отключена", HttpStatus.BAD_REQUEST);
        }

        Payment payment = Payment.builder()
                .user(user)
                .cabinet(cabinet)
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
                "cab-" + cabinet.getId() + "-pay-" + payment.getId(),
                tochkaResult.getStatus()
        ));
        paymentRepository.save(payment);

        log.info("Создан платёж id={} operationId={} cabinetId={} plan={}",
                payment.getId(), tochkaResult.getOperationId(), cabinet.getId(), plan.getCode());

        return InitiatePaymentResponse.builder()
                .paymentId(payment.getId())
                .paymentUrl(tochkaResult.getPaymentLink())
                .build();
    }

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

    @Transactional
    public void completePaymentByOperationId(String operationId, String tochkaStatus) {
        if (operationId == null || operationId.isBlank()) {
            return;
        }
        Payment payment = paymentRepository.findByExternalId(operationId).orElse(null);
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
            if (!hasPlanSnapshot(payment) && !isAbPackCode(payment.getPlanCode())) {
                log.error("Plan snapshot missing for payment id={}", payment.getId());
                paymentRepository.save(payment);
                return;
            }
            applySuccessfulPayment(payment);
            paymentRepository.save(payment);
            return;
        }

        if (tochkaStatus != null && TOCHKA_FAILED_STATUSES.contains(tochkaStatus)) {
            payment.setStatus(PaymentStatus.FAILED.getDbValue());
            paymentRepository.save(payment);
            log.info("Платёж id={} отмечен failed, tochkaStatus={}", payment.getId(), tochkaStatus);
        }
    }

    /**
     * Создаёт или обновляет подписку kind MAIN/CAMPAIGN/AB_PACK на кабинет.
     * Для AB_PACK — бессрочная услуга ({@code expires_at = null}), одна активная запись на кабинет.
     */
    @Transactional
    public Subscription createOrExtendKindSubscription(User user, Cabinet cabinet, Plan plan) {
        LocalDateTime now = LocalDateTime.now();
        PlanKind kind = plan.getKind() != null ? plan.getKind() : PlanKind.CAMPAIGN;

        if (kind == PlanKind.AB_PACK) {
            return upsertAbPackSubscription(user, cabinet, plan, now);
        }

        Subscription current = subscriptionRepository
                .findFirstActiveByCabinetIdAndKind(cabinet.getId(), kind, ACTIVE_STATUSES, now)
                .orElse(null);

        if (current != null) {
            LocalDateTime base = now;
            if (SubscriptionSupport.hasFutureExpiry(current, now)) {
                base = current.getExpiresAt();
            }
            // FREE бессрочный → PRO: ставим новый период от now
            if (PlanCodes.ANALYTICS_FREE.equals(
                    current.getPlan() != null ? current.getPlan().getCode() : null)
                    && PlanCodes.PRO_MONTH.equals(plan.getCode())) {
                base = now;
            }
            current.setExpiresAt(plan.getPriceRub().compareTo(BigDecimal.ZERO) == 0
                    && PlanCodes.ANALYTICS_FREE.equals(plan.getCode())
                    ? null
                    : SubscriptionPeriodUtils.addPlanPeriod(base, plan));
            current.setStatus(plan.getPriceRub().compareTo(BigDecimal.ZERO) == 0 ? "trial" : "active");
            current.setPlan(plan);
            return subscriptionRepository.save(current);
        }

        Subscription subscription = Subscription.builder()
                .user(user)
                .cabinet(cabinet)
                .plan(plan)
                .status(plan.getPriceRub().compareTo(BigDecimal.ZERO) == 0 ? "trial" : "active")
                .startedAt(now)
                .expiresAt(PlanCodes.ANALYTICS_FREE.equals(plan.getCode())
                        ? null
                        : SubscriptionPeriodUtils.addPlanPeriod(now, plan))
                .build();
        return subscriptionRepository.save(subscription);
    }

    /**
     * Одна активная запись услуги А/Б на кабинет: при новой покупке обновляется план (последний пакет).
     */
    private Subscription upsertAbPackSubscription(User user, Cabinet cabinet, Plan plan, LocalDateTime now) {
        boolean free = plan.getPriceRub() == null || plan.getPriceRub().compareTo(BigDecimal.ZERO) == 0;
        Subscription current = subscriptionRepository
                .findFirstActiveByCabinetIdAndKind(cabinet.getId(), PlanKind.AB_PACK, ACTIVE_STATUSES, now)
                .orElse(null);
        if (current != null) {
            current.setPlan(plan);
            current.setStatus(free ? "trial" : "active");
            current.setExpiresAt(null);
            return subscriptionRepository.save(current);
        }
        return subscriptionRepository.save(Subscription.builder()
                .user(user)
                .cabinet(cabinet)
                .plan(plan)
                .status(free ? "trial" : "active")
                .startedAt(now)
                .expiresAt(null)
                .build());
    }

    @Transactional
    public Subscription createOrExtendSubscriptionFromPayment(Payment payment) {
        applySuccessfulPayment(payment);
        return payment.getSubscription();
    }

    private void applySuccessfulPayment(Payment payment) {
        Plan catalogPlan = payment.getPlanCode() != null
                ? planRepository.findByCode(payment.getPlanCode()).orElse(null)
                : null;
        Cabinet cabinet = payment.getCabinet();
        if (cabinet == null) {
            log.error("Payment id={} без cabinet_id — некуда начислить", payment.getId());
            return;
        }

        if (isAbPack(catalogPlan, payment.getPlanCode())) {
            int credits = resolveAbCredits(catalogPlan, payment.getPlanCode());
            abTestQuotaService.addCredits(cabinet, credits);
            if (catalogPlan != null) {
                Subscription abSub = createOrExtendKindSubscription(payment.getUser(), cabinet, catalogPlan);
                payment.setSubscription(abSub);
            } else {
                log.warn("Платёж id={}: план А/Б {} не найден в каталоге — подписка не создана",
                        payment.getId(), payment.getPlanCode());
            }
            log.info("Платёж id={} начислил {} А/Б кредитов cabinetId={}", payment.getId(), credits, cabinet.getId());
            return;
        }

        if (catalogPlan == null) {
            log.error("Catalog plan missing for payment id={} code={}", payment.getId(), payment.getPlanCode());
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        PlanKind kind = catalogPlan.getKind() != null ? catalogPlan.getKind() : PlanKind.CAMPAIGN;
        Subscription current = subscriptionRepository
                .findFirstActiveByCabinetIdAndKind(cabinet.getId(), kind, ACTIVE_STATUSES, now)
                .orElse(null);

        LocalDateTime periodEnd = SubscriptionPeriodUtils.addPlanPeriod(
                now, payment.getPeriodDays(), payment.getPeriodType());

        if (current != null) {
            LocalDateTime base = SubscriptionSupport.hasFutureExpiry(current, now)
                    ? current.getExpiresAt()
                    : now;
            if (PlanCodes.ANALYTICS_FREE.equals(
                    current.getPlan() != null ? current.getPlan().getCode() : null)
                    && PlanCodes.PRO_MONTH.equals(catalogPlan.getCode())) {
                base = now;
            }
            current.setExpiresAt(SubscriptionPeriodUtils.addPlanPeriod(
                    base, payment.getPeriodDays(), payment.getPeriodType()));
            current.setStatus("active");
            current.setPlan(catalogPlan);
            current = subscriptionRepository.save(current);
            payment.setSubscription(current);
            log.info("Платёж id={} продлил подписку id={} cabinetId={} до {}",
                    payment.getId(), current.getId(), cabinet.getId(), current.getExpiresAt());
            return;
        }

        Subscription subscription = Subscription.builder()
                .user(payment.getUser())
                .cabinet(cabinet)
                .plan(catalogPlan)
                .status("active")
                .startedAt(now)
                .expiresAt(periodEnd)
                .build();
        subscription = subscriptionRepository.save(subscription);
        payment.setSubscription(subscription);
        log.info("Платёж id={} создал подписку id={} cabinetId={} до {}",
                payment.getId(), subscription.getId(), cabinet.getId(), subscription.getExpiresAt());
    }

    private Cabinet requireOwnedCabinet(User user, Long cabinetId) {
        if (cabinetId == null) {
            throw new UserException("cabinetId обязателен", HttpStatus.BAD_REQUEST);
        }
        Cabinet cabinet = cabinetRepository.findById(cabinetId)
                .orElseThrow(() -> new UserException("Кабинет не найден", HttpStatus.NOT_FOUND));
        if (user.getRole() != Role.ADMIN
                && (cabinet.getUser() == null || !cabinet.getUser().getId().equals(user.getId()))) {
            throw new UserException("Оплату может инициировать только владелец кабинета", HttpStatus.FORBIDDEN);
        }
        return cabinet;
    }

    private boolean hasPlanSnapshot(Payment payment) {
        return payment.getPeriodDays() != null && payment.getPeriodType() != null;
    }

    private boolean isAbPack(Plan plan, String planCode) {
        if (plan != null && plan.getKind() == PlanKind.AB_PACK) {
            return true;
        }
        return isAbPackCode(planCode);
    }

    private boolean isAbPackCode(String planCode) {
        return planCode != null && planCode.startsWith(PlanCodes.AB_PACK_PREFIX);
    }

    private int resolveAbCredits(Plan plan, String planCode) {
        if (plan != null && plan.getCreditAmount() != null && plan.getCreditAmount() > 0) {
            return plan.getCreditAmount();
        }
        if (PlanCodes.AB_PACK_1.equals(planCode)) {
            return 1;
        }
        if (PlanCodes.AB_PACK_5.equals(planCode)) {
            return 5;
        }
        if (PlanCodes.AB_PACK_10.equals(planCode)) {
            return 10;
        }
        return 0;
    }

    private void validateSellerCanPay(User user) {
        if (user.getRole() != Role.USER && user.getRole() != Role.ADMIN) {
            throw new UserException("Оплату может инициировать только владелец кабинета", HttpStatus.FORBIDDEN);
        }
        if (user.getRole() == Role.USER && !Boolean.TRUE.equals(user.getEmailConfirmed())) {
            throw new UserException("Подтвердите почту перед оплатой", HttpStatus.FORBIDDEN);
        }
    }

    private Plan loadActivePlan(Long planId) {
        Plan plan = planRepository.findById(planId)
                .orElseThrow(() -> new UserException("План не найден: " + planId, HttpStatus.NOT_FOUND));
        if (!Boolean.TRUE.equals(plan.getIsActive())) {
            throw new UserException("План недоступен", HttpStatus.BAD_REQUEST);
        }
        return plan;
    }

    private String buildPaymentDescription(Plan plan) {
        if (plan.getKind() == PlanKind.MAIN) {
            return "Тариф «" + plan.getName() + "»";
        }
        if (plan.getKind() == PlanKind.AB_PACK) {
            return "Пакет А/Б тестов: " + plan.getName();
        }
        return "Услуга «Управление РК»: " + plan.getName();
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
        if (payment.getCabinet() == null || isAbPackCode(payment.getPlanCode())) {
            return null;
        }
        PlanKind kind = planRepository.findByCode(payment.getPlanCode())
                .map(Plan::getKind)
                .orElse(PlanKind.CAMPAIGN);
        return subscriptionRepository
                .findFirstActiveByCabinetIdAndKind(
                        payment.getCabinet().getId(),
                        kind,
                        ACTIVE_STATUSES,
                        LocalDateTime.now()
                )
                .map(Subscription::getExpiresAt)
                .orElse(null);
    }
}
