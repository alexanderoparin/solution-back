package ru.oparin.solution.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.oparin.solution.dto.InitiatePaymentResponse;
import ru.oparin.solution.exception.UserException;
import ru.oparin.solution.model.Payment;
import ru.oparin.solution.model.Plan;
import ru.oparin.solution.model.Subscription;
import ru.oparin.solution.model.User;
import ru.oparin.solution.repository.PaymentRepository;
import ru.oparin.solution.repository.PlanRepository;
import ru.oparin.solution.repository.SubscriptionRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Сервис оплаты подписки через Робокассу.
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

    /**
     * Инициирует платёж: создаёт запись в БД и возвращает URL для редиректа в Робокассу.
     */
    @Transactional
    public InitiatePaymentResponse initiatePayment(User user, Long planId) {
        Plan plan = planRepository.findById(planId)
                .orElseThrow(() -> new UserException("План не найден: " + planId, HttpStatus.NOT_FOUND));
        if (!Boolean.TRUE.equals(plan.getIsActive())) {
            throw new UserException("План недоступен для оплаты", HttpStatus.BAD_REQUEST);
        }

        Payment payment = Payment.builder()
                .user(user)
                .amount(plan.getPriceRub())
                .currency("RUB")
                .status("pending")
                .metadata(metadataWithPlanId(planId))
                .build();
        payment = paymentRepository.save(payment);

        String invId = payment.getId().toString();
        String description = String.format("Подписка: тариф '%s' доступ на %d дней", plan.getDescription(), plan.getPeriodDays());
        String paymentUrl = robokassaService.buildPaymentUrl(plan.getPriceRub(), invId, description);

        log.info("Создан платёж {} для user {} plan {}", payment.getId(), user.getId(), planId);
        return InitiatePaymentResponse.builder()
                .paymentUrl(paymentUrl)
                .paymentId(payment.getId())
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

        if ("success".equals(payment.getStatus())) {
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
        payment.setStatus("success");
        payment.setPaidAt(LocalDateTime.now());
        payment.setSubscription(subscription);
        payment.setExternalId(invId);
        paymentRepository.save(payment);

        log.info("Платёж {} успешно обработан, подписка {} до {}", paymentId, subscription.getId(), subscription.getExpiresAt());
        return true;
    }

    private String metadataWithPlanId(Long planId) {
        try {
            return objectMapper.writeValueAsString(Map.of("planId", planId));
        } catch (Exception e) {
            throw new RuntimeException("Не удалось сформировать metadata", e);
        }
    }

    private Long parsePlanIdFromMetadata(String metadata) {
        if (metadata == null || metadata.isBlank()) return null;
        try {
            JsonNode node = objectMapper.readTree(metadata);
            if (node.has("planId")) return node.get("planId").asLong();
        } catch (Exception e) {
            log.warn("Ошибка парсинга metadata платежа: {}", e.getMessage());
        }
        return null;
    }

    private Subscription createOrExtendSubscription(User user, Plan plan) {
        LocalDateTime now = LocalDateTime.now();
        Subscription current = subscriptionRepository.findFirstByUser_IdAndStatusInAndExpiresAtAfterOrderByExpiresAtDesc(
                user.getId(), ACTIVE_STATUSES, now
        ).orElse(null);

        if (current != null && current.getExpiresAt().isAfter(now)) {
            current.setExpiresAt(current.getExpiresAt().plusDays(plan.getPeriodDays()));
            current.setStatus("active");
            current.setPlan(plan);
            return subscriptionRepository.save(current);
        }

        Subscription subscription = Subscription.builder()
                .user(user)
                .plan(plan)
                .status("active")
                .startedAt(now)
                .expiresAt(now.plusDays(plan.getPeriodDays()))
                .build();
        return subscriptionRepository.save(subscription);
    }
}
