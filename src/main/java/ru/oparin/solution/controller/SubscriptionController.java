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
import ru.oparin.solution.service.CabinetBillingService;
import ru.oparin.solution.service.PlanMapper;
import ru.oparin.solution.service.SubscriptionPaymentService;
import ru.oparin.solution.service.UserService;

import java.util.ArrayList;
import java.util.List;

/**
 * API подписок, тарифов и услуг кабинета.
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

    /**
     * Каталог планов. Опционально фильтр по kind: MAIN | CAMPAIGN | AB_PACK.
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
                    .map(PlanMapper::toDto)
                    .toList();
        } else {
            list = new ArrayList<>();
            list.addAll(planRepository.findByIsActiveTrueAndKindOrderBySortOrderAsc(PlanKind.MAIN).stream()
                    .map(PlanMapper::toDto)
                    .toList());
            if (subscriptionProperties.isCampaignManagementEnabled()) {
                list.addAll(planRepository.findByIsActiveTrueAndKindOrderBySortOrderAsc(PlanKind.CAMPAIGN).stream()
                        .map(PlanMapper::toDto)
                        .toList());
            }
            list.addAll(planRepository.findByIsActiveTrueAndKindOrderBySortOrderAsc(PlanKind.AB_PACK).stream()
                    .map(PlanMapper::toDto)
                    .toList());
        }
        return ResponseEntity.ok(list);
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
     * Активация бесплатного плана услуги кабинета.
     */
    @PostMapping("/activate")
    public ResponseEntity<ActivatePlanResponse> activatePlan(
            @Valid @RequestBody ActivatePlanRequest request,
            Authentication authentication
    ) {
        User user = userService.findByEmail(authentication.getName());
        return ResponseEntity.ok(subscriptionPaymentService.activateFreePlan(
                user, request.getPlanId(), request.getCabinetId()));
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
        return ResponseEntity.ok(subscriptionPaymentService.initiatePaidPlan(
                user, request.getPlanId(), request.getCabinetId()));
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

    /**
     * Явно подключает бесплатный пакет А/Б тестов для кабинета (план ab_pack_free).
     */
    @PostMapping("/cabinet/{cabinetId}/ab-tests/activate-free")
    public ResponseEntity<WbAbTestQuotaDto> activateAbFreeQuota(
            @PathVariable Long cabinetId,
            Authentication authentication
    ) {
        User user = userService.findByEmail(authentication.getName());
        return ResponseEntity.ok(cabinetBillingService.activateAbFreeQuota(user, cabinetId));
    }
}
