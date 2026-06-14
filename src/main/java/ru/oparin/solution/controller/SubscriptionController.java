package ru.oparin.solution.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import ru.oparin.solution.config.SubscriptionProperties;
import ru.oparin.solution.dto.ActivatePlanRequest;
import ru.oparin.solution.dto.ActivatePlanResponse;
import ru.oparin.solution.dto.PlanDto;
import ru.oparin.solution.dto.SubscriptionStatusResponse;
import ru.oparin.solution.model.PlanProductCode;
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
     * Список активных тарифных планов.
     *
     * @param product код продукта (например CAMPAIGN_MANAGE); без параметра — legacy-поведение при глобальной оплате
     */
    @GetMapping("/plans")
    public ResponseEntity<List<PlanDto>> getPlans(
            @RequestParam(required = false) String product
    ) {
        if (PlanProductCode.CAMPAIGN_MANAGE.equals(product)) {
            if (!subscriptionProperties.isCampaignManagementEnabled()) {
                return ResponseEntity.ok(List.of());
            }
            List<PlanDto> list = planRepository.findByIsActiveTrueAndProductCodeOrderBySortOrderAsc(product)
                    .stream()
                    .map(PlanMapper::toDto)
                    .toList();
            return ResponseEntity.ok(list);
        }
        if (!subscriptionProperties.isBillingEnabled()) {
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
}
