package ru.oparin.solution.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import ru.oparin.solution.config.SubscriptionProperties;
import ru.oparin.solution.dto.*;
import ru.oparin.solution.model.PlanKind;
import ru.oparin.solution.model.User;
import ru.oparin.solution.repository.PlanRepository;
import ru.oparin.solution.service.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * API подписок, тарифов и услуг.
 */
@RestController
@RequestMapping("/subscription")
@RequiredArgsConstructor
public class SubscriptionController {

    private final SubscriptionPaymentService subscriptionPaymentService;
    private final PlanRepository planRepository;
    private final UserService userService;
    private final SubscriptionProperties subscriptionProperties;
    private final CabinetBillingService cabinetBillingService;
    private final UserBillingOverviewService userBillingOverviewService;

    /**
     * Каталог планов для покупки. Опционально фильтр по kind: MAIN | CAMPAIGN | AB_PACK.
     * Бесплатные / trial планы не отдаются.
     */
    @GetMapping("/plans")
    public ResponseEntity<List<PlanDto>> getPlans(
            @RequestParam(required = false) String kind
    ) {
        List<PlanDto> list;
        if (kind != null && !kind.isBlank()) {
            PlanKind planKind = PlanKind.valueOf(kind.trim().toUpperCase());
            if (planKind == PlanKind.CAMPAIGN && !subscriptionProperties.isCampaignManagementEnabled()) {
                return ResponseEntity.ok(List.of());
            }
            list = planRepository.findByIsActiveTrueAndKindOrderBySortOrderAsc(planKind).stream()
                    .filter(this::isPurchasable)
                    .map(PlanMapper::toDto)
                    .toList();
        } else {
            list = new ArrayList<>();
            list.addAll(planRepository.findByIsActiveTrueAndKindOrderBySortOrderAsc(PlanKind.MAIN).stream()
                    .filter(this::isPurchasable)
                    .map(PlanMapper::toDto)
                    .toList());
            if (subscriptionProperties.isCampaignManagementEnabled()) {
                list.addAll(planRepository.findByIsActiveTrueAndKindOrderBySortOrderAsc(PlanKind.CAMPAIGN).stream()
                        .filter(this::isPurchasable)
                        .map(PlanMapper::toDto)
                        .toList());
            }
            list.addAll(planRepository.findByIsActiveTrueAndKindOrderBySortOrderAsc(PlanKind.AB_PACK).stream()
                    .filter(this::isPurchasable)
                    .map(PlanMapper::toDto)
                    .toList());
        }
        return ResponseEntity.ok(list);
    }

    /**
     * Сводка подписок пользователя: кабинеты, активные пакеты, квота А/Б.
     */
    @GetMapping("/overview")
    public ResponseEntity<UserBillingOverviewDto> getOverview(Authentication authentication) {
        User user = userService.findByEmail(authentication.getName());
        return ResponseEntity.ok(userBillingOverviewService.buildOverview(user));
    }

    /**
     * Статус тарифов и услуг выбранного кабинета.
     */
    @GetMapping("/cabinet/{cabinetId}/status")
    public ResponseEntity<CabinetBillingStatusDto> getCabinetBillingStatus(
            @PathVariable Long cabinetId,
            Authentication authentication
    ) {
        User user = userService.findByEmail(authentication.getName());
        return ResponseEntity.ok(cabinetBillingService.buildStatus(user, cabinetId));
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
     * Инициация оплаты платного плана / услуги / пакета А/Б.
     */
    @PostMapping("/initiate-payment")
    public ResponseEntity<InitiatePaymentResponse> initiatePayment(
            @Valid @RequestBody InitiatePaymentRequest request,
            Authentication authentication
    ) {
        User user = userService.findByEmail(authentication.getName());
        List<Long> cabinetIds = request.getCabinetIds();
        if ((cabinetIds == null || cabinetIds.isEmpty()) && request.getCabinetId() != null) {
            cabinetIds = List.of(request.getCabinetId());
        }
        return ResponseEntity.ok(subscriptionPaymentService.initiatePaidPlan(
                user, request.getPlanId(), cabinetIds));
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

    private boolean isPurchasable(ru.oparin.solution.model.Plan plan) {
        return plan.getPriceRub() != null && plan.getPriceRub().compareTo(BigDecimal.ZERO) > 0;
    }
}
