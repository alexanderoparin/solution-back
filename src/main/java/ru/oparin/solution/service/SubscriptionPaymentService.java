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
import ru.oparin.solution.dto.InitiatePaymentResponse;
import ru.oparin.solution.dto.PaymentStatusResponse;
import ru.oparin.solution.exception.UserException;
import ru.oparin.solution.model.*;
import ru.oparin.solution.repository.*;
import ru.oparin.solution.service.tochka.TochkaAcquiringService;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Оплата тарифов/услуг через Точка Банк: multi-cabinet для MAIN/CAMPAIGN, A/B на пользователя.
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
    private final PaymentCabinetRepository paymentCabinetRepository;
    private final CabinetRepository cabinetRepository;
    private final SubscriptionProperties subscriptionProperties;
    private final TochkaProperties tochkaProperties;
    private final TochkaAcquiringService tochkaAcquiringService;
    private final WbAbTestQuotaService abTestQuotaService;
    private final PackageManagementService packageManagementService;
    private final ObjectMapper objectMapper;

    @Value("${app.brand-name:Clicki}")
    private String brandName;

    /**
     * Создаёт платёж и ссылку Точка Банк.
     *
     * @param cabinetIds кабинеты для MAIN/CAMPAIGN; для AB_PACK игнорируются
     */
    @Transactional
    public InitiatePaymentResponse initiatePaidPlan(User user, Long planId, List<Long> cabinetIds) {
        validateSellerCanPay(user);
        if (!tochkaProperties.isConfiguredForPayments()) {
            throw new UserException("Оплата временно недоступна", HttpStatus.SERVICE_UNAVAILABLE);
        }

        Plan plan = loadActivePlan(planId);
        if (plan.getPriceRub().compareTo(BigDecimal.ZERO) <= 0) {
            throw new UserException("План недоступен для оплаты", HttpStatus.BAD_REQUEST);
        }
        if (plan.getKind() == PlanKind.CAMPAIGN && !subscriptionProperties.isCampaignManagementEnabled()) {
            throw new UserException("Подписка на Управление РК отключена", HttpStatus.BAD_REQUEST);
        }

        List<Cabinet> cabinets = List.of();
        if (plan.getKind() == PlanKind.AB_PACK) {
            cabinets = List.of();
        } else {
            cabinets = resolveAndValidateCabinets(user, plan, cabinetIds);
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
                .description(buildPaymentDescription(plan, cabinets.size()))
                .build();
        payment = paymentRepository.save(payment);

        for (Cabinet cabinet : cabinets) {
            paymentCabinetRepository.save(PaymentCabinet.builder()
                    .paymentId(payment.getId())
                    .cabinetId(cabinet.getId())
                    .payment(payment)
                    .cabinet(cabinet)
                    .build());
        }

        var tochkaResult = tochkaAcquiringService.createSubscriptionPayment(
                user, plan, payment.getId(), brandName);

        String linkId = cabinets.isEmpty()
                ? "user-" + user.getId() + "-pay-" + payment.getId()
                : "cabs-" + cabinets.size() + "-pay-" + payment.getId();
        payment.setExternalId(tochkaResult.getOperationId());
        payment.setMetadata(buildPaymentMetadata(
                tochkaResult.getOperationId(),
                tochkaResult.getPaymentLink(),
                linkId,
                tochkaResult.getStatus()
        ));
        paymentRepository.save(payment);

        log.info("Создан платёж id={} operationId={} cabinets={} plan={}",
                payment.getId(), tochkaResult.getOperationId(), cabinets.size(), plan.getCode());

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
                .planName(payment.getPlanName())
                .planCode(payment.getPlanCode())
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
            if (!hasPlanSnapshot(payment) && !PlanCodes.isAbPackCode(payment.getPlanCode())) {
                log.error("Plan snapshot missing for payment id={}", payment.getId());
                paymentRepository.save(payment);
                return;
            }
            applySuccessfulPayment(payment);
            paymentRepository.save(payment);
            log.info("Платёж id={} отмечен success, tochkaStatus={}", payment.getId(), tochkaStatus);
            return;
        }

        if (tochkaStatus != null && TOCHKA_FAILED_STATUSES.contains(tochkaStatus)) {
            payment.setStatus(PaymentStatus.FAILED.getDbValue());
            paymentRepository.save(payment);
            log.info("Платёж id={} отмечен failed, tochkaStatus={}", payment.getId(), tochkaStatus);
        }
    }

    /**
     * Создаёт или продлевает подписку MAIN/CAMPAIGN на кабинет.
     */
    @Transactional
    public Subscription createOrExtendKindSubscription(User user, Cabinet cabinet, Plan plan) {
        return createOrExtendKindSubscription(user, cabinet, plan, null);
    }

    @Transactional
    public Subscription createOrExtendKindSubscription(User user, Cabinet cabinet, Plan plan, Payment payment) {
        LocalDateTime now = LocalDateTime.now();
        PlanKind kind = plan.getKind() != null ? plan.getKind() : PlanKind.CAMPAIGN;

        if (kind == PlanKind.AB_PACK) {
            return upsertAbPackSubscription(user, cabinet, plan, now, payment);
        }

        Subscription current = subscriptionRepository
                .findFirstActiveByCabinetIdAndKind(cabinet.getId(), kind, ACTIVE_STATUSES, now)
                .orElse(null);

        if (current != null) {
            LocalDateTime base = now;
            if (SubscriptionSupport.hasFutureExpiry(current, now)) {
                base = current.getExpiresAt();
            }
            if (isFreeMain(current) && PlanCodes.isProCode(plan.getCode())) {
                base = now;
            }
            current.setExpiresAt(isFreeMainPlan(plan)
                    ? null
                    : SubscriptionPeriodUtils.addPlanPeriod(base, plan));
            current.setStatus("active");
            current.setPlan(plan);
            if (payment != null) {
                current.setPayment(payment);
            }
            return subscriptionRepository.save(current);
        }

        Subscription subscription = Subscription.builder()
                .user(user)
                .cabinet(cabinet)
                .plan(plan)
                .payment(payment)
                .status("active")
                .startedAt(now)
                .expiresAt(isFreeMainPlan(plan)
                        ? null
                        : SubscriptionPeriodUtils.addPlanPeriod(now, plan))
                .build();
        return subscriptionRepository.save(subscription);
    }

    private Subscription upsertAbPackSubscription(
            User user, Cabinet cabinet, Plan plan, LocalDateTime now, Payment payment
    ) {
        Subscription current = subscriptionRepository
                .findFirstActiveByCabinetIdAndKind(cabinet.getId(), PlanKind.AB_PACK, ACTIVE_STATUSES, now)
                .orElse(null);
        if (current != null) {
            current.setPlan(plan);
            current.setStatus("active");
            current.setExpiresAt(null);
            if (payment != null) {
                current.setPayment(payment);
            }
            return subscriptionRepository.save(current);
        }
        return subscriptionRepository.save(Subscription.builder()
                .user(user)
                .cabinet(cabinet)
                .plan(plan)
                .payment(payment)
                .status("active")
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
        if (packageManagementService.applyUpgradeIfNeeded(payment)) {
            return;
        }

        Plan catalogPlan = payment.getPlanCode() != null
                ? planRepository.findByCode(payment.getPlanCode()).orElse(null)
                : null;

        if (isAbPack(catalogPlan, payment.getPlanCode())) {
            int credits = resolveAbCredits(catalogPlan, payment.getPlanCode());
            Long userId = payment.getUser() != null ? payment.getUser().getId() : null;
            abTestQuotaService.addCreditsToUser(userId, credits);
            log.info("Платёж id={} начислил {} А/Б кредитов userId={}", payment.getId(), credits, userId);
            return;
        }

        if (catalogPlan == null) {
            log.error("Catalog plan missing for payment id={} code={}", payment.getId(), payment.getPlanCode());
            return;
        }

        List<Cabinet> cabinets = resolvePaymentCabinets(payment);
        if (cabinets.isEmpty()) {
            log.error("Payment id={} без кабинетов — некуда начислить", payment.getId());
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        PlanKind kind = catalogPlan.getKind() != null ? catalogPlan.getKind() : PlanKind.CAMPAIGN;
        Subscription first = null;

        for (Cabinet cabinet : cabinets) {
            Subscription current = subscriptionRepository
                    .findFirstActiveByCabinetIdAndKind(cabinet.getId(), kind, ACTIVE_STATUSES, now)
                    .orElse(null);

            LocalDateTime periodEnd = SubscriptionPeriodUtils.addPlanPeriod(
                    now, payment.getPeriodDays(), payment.getPeriodType());

            if (current != null) {
                LocalDateTime base = SubscriptionSupport.hasFutureExpiry(current, now)
                        ? current.getExpiresAt()
                        : now;
                if (isFreeMain(current) && PlanCodes.isProCode(catalogPlan.getCode())) {
                    base = now;
                }
                current.setExpiresAt(SubscriptionPeriodUtils.addPlanPeriod(
                        base, payment.getPeriodDays(), payment.getPeriodType()));
                current.setStatus("active");
                current.setPlan(catalogPlan);
                current.setPayment(payment);
                current = subscriptionRepository.save(current);
                if (first == null) {
                    first = current;
                }
                log.info("Платёж id={} продлил подписку id={} cabinetId={} до {}",
                        payment.getId(), current.getId(), cabinet.getId(), current.getExpiresAt());
            } else {
                Subscription subscription = Subscription.builder()
                        .user(payment.getUser())
                        .cabinet(cabinet)
                        .plan(catalogPlan)
                        .payment(payment)
                        .status("active")
                        .startedAt(now)
                        .expiresAt(periodEnd)
                        .build();
                subscription = subscriptionRepository.save(subscription);
                if (first == null) {
                    first = subscription;
                }
                log.info("Платёж id={} создал подписку id={} cabinetId={} до {}",
                        payment.getId(), subscription.getId(), cabinet.getId(), subscription.getExpiresAt());
            }
        }
        if (first != null) {
            payment.setSubscription(first);
        }
    }

    private List<Cabinet> resolvePaymentCabinets(Payment payment) {
        List<PaymentCabinet> links = paymentCabinetRepository.findByPaymentId(payment.getId());
        if (!links.isEmpty()) {
            List<Cabinet> result = new ArrayList<>();
            for (PaymentCabinet link : links) {
                Cabinet cabinet = link.getCabinet();
                if (cabinet == null && link.getCabinetId() != null) {
                    cabinet = cabinetRepository.findById(link.getCabinetId()).orElse(null);
                }
                if (cabinet != null) {
                    result.add(cabinet);
                }
            }
            return result;
        }
        return List.of();
    }

    private List<Cabinet> resolveAndValidateCabinets(User user, Plan plan, List<Long> cabinetIds) {
        if (cabinetIds == null || cabinetIds.isEmpty()) {
            throw new UserException("Выберите хотя бы один кабинет", HttpStatus.BAD_REQUEST);
        }
        Set<Long> uniqueIds = new LinkedHashSet<>(cabinetIds);
        int count = uniqueIds.size();
        Integer min = plan.getMinCabinets();
        Integer max = plan.getMaxCabinets();
        if (min != null && count < min) {
            throw new UserException(
                    "Для этого тарифа нужно выбрать не меньше " + min + " кабинетов",
                    HttpStatus.BAD_REQUEST);
        }
        if (max != null && count > max) {
            throw new UserException(
                    "Для этого тарифа можно выбрать не больше " + max + " кабинетов",
                    HttpStatus.BAD_REQUEST);
        }

        List<Cabinet> cabinets = new ArrayList<>();
        for (Long cabinetId : uniqueIds) {
            cabinets.add(requireOwnedCabinet(user, cabinetId));
        }
        return cabinets;
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
        return PlanCodes.isAbPackCode(planCode);
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

    private String buildPaymentDescription(Plan plan, int cabinetCount) {
        if (plan.getKind() == PlanKind.MAIN) {
            return cabinetCount > 0
                    ? "Полный доступ (" + cabinetCount + " каб.)"
                    : "Тариф «" + plan.getName() + "»";
        }
        if (plan.getKind() == PlanKind.AB_PACK) {
            return "Пакет А/Б тестов: " + plan.getName();
        }
        return cabinetCount > 0
                ? "Управление РК (" + cabinetCount + " каб.)"
                : "Услуга «Управление РК»: " + plan.getName();
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
        if (PlanCodes.isAbPackCode(payment.getPlanCode())) {
            return null;
        }
        List<Cabinet> cabinets = resolvePaymentCabinets(payment);
        if (cabinets.isEmpty()) {
            return null;
        }
        PlanKind kind = planRepository.findByCode(payment.getPlanCode())
                .map(Plan::getKind)
                .orElse(PlanKind.CAMPAIGN);
        return subscriptionRepository
                .findFirstActiveByCabinetIdAndKind(
                        cabinets.get(0).getId(),
                        kind,
                        ACTIVE_STATUSES,
                        LocalDateTime.now()
                )
                .map(Subscription::getExpiresAt)
                .orElse(null);
    }

    private static boolean isFreeMain(Subscription subscription) {
        return subscription.getPlan() != null
                && PlanCodes.ANALYTICS_FREE.equals(subscription.getPlan().getCode());
    }

    private static boolean isFreeMainPlan(Plan plan) {
        return plan != null && PlanCodes.ANALYTICS_FREE.equals(plan.getCode());
    }
}
