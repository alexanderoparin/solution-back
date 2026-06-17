package ru.oparin.solution.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import ru.oparin.solution.config.SubscriptionProperties;
import ru.oparin.solution.dto.*;
import ru.oparin.solution.model.User;
import ru.oparin.solution.repository.PlanRepository;
import ru.oparin.solution.service.PlanMapper;
import ru.oparin.solution.service.SubscriptionPaymentService;
import ru.oparin.solution.service.UserService;

import java.util.List;

/**
 * API подписок и тарифных планов.
 */
@RestController
@RequestMapping("/subscription")
@RequiredArgsConstructor
public class SubscriptionController {

    private final SubscriptionPaymentService subscriptionPaymentService;
    private final PlanRepository planRepository;
    private final UserService userService;
    private final SubscriptionProperties subscriptionProperties;

    /**
     * Список активных тарифов «Управление РК».
     */
    @GetMapping("/plans")
    public ResponseEntity<List<PlanDto>> getPlans() {
        if (!subscriptionProperties.isCampaignManagementEnabled()) {
            return ResponseEntity.ok(List.of());
        }
        List<PlanDto> list = planRepository.findByIsActiveTrueOrderBySortOrderAsc()
                .stream()
                .map(PlanMapper::toDto)
                .toList();
        return ResponseEntity.ok(list);
    }

    /**
     * Статус модуля оплаты (для фронта).
     */
    @GetMapping("/status")
    public ResponseEntity<SubscriptionStatusResponse> getStatus() {
        return ResponseEntity.ok(
                SubscriptionStatusResponse.builder()
                        .billingEnabled(subscriptionProperties.isBillingEnabled())
                        .campaignManagementEnabled(subscriptionProperties.isCampaignManagementEnabled())
                        .build()
        );
    }

    /**
     * Активация бесплатного плана.
     */
    @PostMapping("/activate")
    public ResponseEntity<ActivatePlanResponse> activatePlan(
            @Valid @RequestBody ActivatePlanRequest request,
            Authentication authentication
    ) {
        User user = userService.findByEmail(authentication.getName());
        return ResponseEntity.ok(subscriptionPaymentService.activateFreePlan(user, request.getPlanId()));
    }

    /**
     * Инициация оплаты платного плана (Точка Банк).
     */
    @PostMapping("/initiate-payment")
    public ResponseEntity<InitiatePaymentResponse> initiatePayment(
            @Valid @RequestBody InitiatePaymentRequest request,
            Authentication authentication
    ) {
        User user = userService.findByEmail(authentication.getName());
        return ResponseEntity.ok(subscriptionPaymentService.initiatePaidPlan(user, request.getPlanId()));
    }

    /**
     * Статус платежа после возврата с платёжной страницы.
     */
    @GetMapping("/payment/{paymentId}/status")
    public ResponseEntity<PaymentStatusResponse> getPaymentStatus(
            @PathVariable Long paymentId,
            Authentication authentication
    ) {
        User user = userService.findByEmail(authentication.getName());
        return ResponseEntity.ok(subscriptionPaymentService.getPaymentStatus(user, paymentId));
    }
}
