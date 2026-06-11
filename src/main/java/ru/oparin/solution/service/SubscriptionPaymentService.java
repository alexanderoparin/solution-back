package ru.oparin.solution.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.oparin.solution.config.SubscriptionProperties;
import ru.oparin.solution.dto.ActivatePlanResponse;
import ru.oparin.solution.dto.InitiatePaymentResponse;
import ru.oparin.solution.exception.UserException;
import ru.oparin.solution.model.*;
import ru.oparin.solution.repository.PaymentRepository;
import ru.oparin.solution.repository.PlanRepository;
import ru.oparin.solution.repository.SubscriptionRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Сервис оплаты подписки через Робокассу и активации бесплатных планов.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SubscriptionPaymentService {

    private static final List<String> ACTIVE_STATUSES = List.of("active", "trial");

    private final PlanRepository planRepository;
    private final PaymentRepository paymentRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final RobokassaService robokassaService;
    private final ObjectMapper objectMapper;
    private final SubscriptionProperties subscriptionProperties;

    /**
     * Инициирует платёж: создаёт запись в БД и возвращает URL для редиректа в Робокассу.
     */
    @Transactional
    public InitiatePaymentResponse initiatePayment(User user, Long planId) {
        assertPaymentAllowed(planId);
        Plan plan = loadPayablePlan(planId);
        if (plan.getPriceRub().compareTo(BigDecimal.ZERO) <= 0) {
            throw new UserException("Для бесплатного плана используйте активацию", HttpStatus.BAD_REQUEST);
        }
        BigDecimal priceRub = plan.getPriceRub();

        String description = String.format("Тариф '%s'. %s", plan.getName(), plan.getDescription());
        Payment payment = Payment.builder()
                .user(user)
                .amount(priceRub)
                .currency("RUB")
                .status(PaymentStatus.PENDING.getDbValue())
                .description(description)
                .metadata(metadataWithPlanId(planId))
                .build();
        payment = paymentRepository.save(payment);

        String invId = payment.getId().toString();
        String paymentUrl = robokassaService.buildPaymentUrl(priceRub, invId, description);

        log.info("Создан платёж №{} для пользователя с ID={} и email '{}', сумма платежа {}",
                payment.getId(), user.getId(), user.getEmail(), priceRub);
        return InitiatePaymentResponse.builder()
                .paymentUrl(paymentUrl)
                .paymentId(payment.getId())
                .build();
    }

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
        return ActivatePlanResponse.builder()
                .subscriptionId(subscription.getId())
                .expiresAt(subscription.getExpiresAt())
                .build();
    }

    /**
     * Обрабатывает Result URL от Робокассы: проверяет подпись и при успехе обновляет платёж и подписку.
     *
     * @return true если подпись верна и платёж обработан (или уже был успешен)
     */
    @Transactional
    public boolean handlePaymentResult(String outSum, String invId, String signature) {
        if (!robokassaService.verifyResultSignature(outSum, invId, signature)) {
            return false;
        }

        Long paymentId = Long.parseLong(invId);
        Payment payment = paymentRepository.findById(paymentId).orElse(null);
        if (payment == null) {
            log.warn("Платёж не найден: {}", paymentId);
            return false;
        }

        if (PaymentStatus.SUCCESS.getDbValue().equalsIgnoreCase(payment.getStatus())) {
            log.info("Платёж {} уже обработан", paymentId);
            return true;
        }

        Long planId = parsePlanIdFromMetadata(payment.getMetadata());
        if (planId == null) {
            log.error("В платеже {} нет planId в metadata", paymentId);
            return false;
        }

        Plan plan = planRepository.findById(planId).orElse(null);
        if (plan == null) {
            log.error("План {} не найден для платежа {}", planId, paymentId);
            return false;
        }

        User user = payment.getUser();
        Subscription subscription = createOrExtendSubscription(user, plan);
        payment.setStatus(PaymentStatus.SUCCESS.getDbValue());
        payment.setPaidAt(LocalDateTime.now());
        payment.setSubscription(subscription);
        payment.setExternalId(invId);
        paymentRepository.save(payment);

        log.info("Платёж {} успешно обработан, подписка {} до {}", paymentId, subscription.getId(), subscription.getExpiresAt());
        return true;
    }

    private void assertPaymentAllowed(Long planId) {
        Plan plan = planRepository.findById(planId).orElse(null);
        if (plan != null && PlanProductCode.CAMPAIGN_MANAGE.equals(plan.getProductCode())) {
            if (!subscriptionProperties.isCampaignManagementEnabled()) {
                throw new UserException("Подписка на Управление РК отключена", HttpStatus.BAD_REQUEST);
            }
            return;
        }
        if (!subscriptionProperties.isBillingEnabled()) {
            throw new UserException("Оплата временно отключена", HttpStatus.BAD_REQUEST);
        }
    }

    private Plan loadPayablePlan(Long planId) {
        Plan plan = planRepository.findById(planId)
                .orElseThrow(() -> new UserException("План не найден: " + planId, HttpStatus.NOT_FOUND));
        if (!Boolean.TRUE.equals(plan.getIsActive())) {
            throw new UserException("План недоступен для оплаты", HttpStatus.BAD_REQUEST);
        }
        return plan;
    }

    private String metadataWithPlanId(Long planId) {
        try {
            return objectMapper.writeValueAsString(Map.of("planId", planId));
        } catch (Exception e) {
            throw new RuntimeException("Не удалось сформировать metadata", e);
        }
    }

    private Long parsePlanIdFromMetadata(String metadata) {
        if (metadata == null || metadata.isBlank()) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(metadata);
            if (node.has("planId")) {
                return node.get("planId").asLong();
            }
        } catch (Exception e) {
            log.warn("Ошибка парсинга metadata платежа: {}", e.getMessage());
        }
        return null;
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
