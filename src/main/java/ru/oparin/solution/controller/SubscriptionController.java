package ru.oparin.solution.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import ru.oparin.solution.config.SubscriptionProperties;
import ru.oparin.solution.dto.InitiatePaymentRequest;
import ru.oparin.solution.dto.InitiatePaymentResponse;
import ru.oparin.solution.dto.PlanDto;
import ru.oparin.solution.dto.SubscriptionStatusResponse;
import ru.oparin.solution.exception.UserException;
import ru.oparin.solution.model.User;
import ru.oparin.solution.repository.PlanRepository;
import ru.oparin.solution.service.SubscriptionPaymentService;
import ru.oparin.solution.service.UserService;

import java.util.List;
import java.util.stream.Collectors;

/**
 * API подписок и оплаты через Робокассу.
 */
@RestController
@RequestMapping("/subscription")
@RequiredArgsConstructor
@Slf4j
public class SubscriptionController {

    private final SubscriptionPaymentService subscriptionPaymentService;
    private final PlanRepository planRepository;
    private final UserService userService;
    private final SubscriptionProperties subscriptionProperties;

    /**
     * Список активных тарифных планов.
     */
    @GetMapping("/plans")
    public ResponseEntity<List<PlanDto>> getPlans() {
        if (!subscriptionProperties.isBillingEnabled()) {
            return ResponseEntity.ok(List.of());
        }
        List<PlanDto> list = planRepository.findByIsActiveTrueOrderBySortOrderAsc()
                .stream()
                .map(p -> PlanDto.builder()
                        .id(p.getId())
                        .name(p.getName())
                        .description(p.getDescription())
                        .priceRub(p.getPriceRub())
                        .periodDays(p.getPeriodDays())
                        .maxCabinets(p.getMaxCabinets())
                        .build())
                .collect(Collectors.toList());
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
                        .build()
        );
    }

    /**
     * Инициация оплаты выбранного плана. Возвращает URL для редиректа в Робокассу.
     * При выключенной оплате возвращает 400.
     */
    @PostMapping("/initiate")
    public ResponseEntity<InitiatePaymentResponse> initiatePayment(
            @Valid @RequestBody InitiatePaymentRequest request,
            Authentication authentication
    ) {
        if (!subscriptionProperties.isBillingEnabled()) {
            throw new UserException("Оплата временно отключена", HttpStatus.BAD_REQUEST);
        }
        User user = userService.findByEmail(authentication.getName());
        InitiatePaymentResponse response = subscriptionPaymentService.initiatePayment(user, request.getPlanId());
        return ResponseEntity.ok(response);
    }

    /**
     * URL результата callback от Робокассы (вызывается сервером Робокассы).
     * Ответ в формате OK[InvId] при успехе — по документации Робокассы.
     */
    @GetMapping("/payment/result")
    public ResponseEntity<String> paymentResult(
            @RequestParam(value = "OutSum", required = false) String outSum,
            @RequestParam(value = "InvId", required = false) String invId,
            @RequestParam(value = "SignatureValue", required = false) String signatureValue
    ) {
        log.info("Result callback: InvId={}, OutSum={}", invId, outSum);

        if (outSum == null || invId == null || signatureValue == null) {
            log.warn("Отсутствуют обязательные параметры в callback Робокассы");
            return ResponseEntity.ok("ERROR");
        }

        boolean ok = subscriptionPaymentService.handlePaymentResult(outSum, invId, signatureValue);
        if (ok) {
            return ResponseEntity.ok("OK" + invId);
        }
        return ResponseEntity.ok("FAIL");
    }
}
