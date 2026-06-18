package ru.oparin.solution.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.oparin.solution.service.SubscriptionPaymentService;
import ru.oparin.solution.service.TochkaWebhookService;
import ru.oparin.solution.service.TochkaWebhookService.TochkaWebhookEvent;

/**
 * Webhook-события от Точка Банк.
 */
@RestController
@RequestMapping("/webhooks")
@RequiredArgsConstructor
@Slf4j
public class TochkaWebhookController {

    private final TochkaWebhookService tochkaWebhookService;
    private final SubscriptionPaymentService subscriptionPaymentService;

    /**
     * Событие acquiringInternetPayment — успешная или неуспешная оплата по платёжной ссылке.
     */
    @PostMapping("/tochka")
    public ResponseEntity<Void> handleTochkaWebhook(@RequestBody String rawBody) {
        TochkaWebhookEvent event = tochkaWebhookService.parseAcquiringPaymentEvent(rawBody);
        if (event == null) {
            return ResponseEntity.badRequest().build();
        }
        if (event.webhookType() != null
                && !event.webhookType().isBlank()
                && !"acquiringInternetPayment".equalsIgnoreCase(event.webhookType())) {
            log.debug("Ignoring Tochka webhook type={}", event.webhookType());
            return ResponseEntity.ok().build();
        }
        if (event.operationId() != null && !event.operationId().isBlank()) {
            log.info("Tochka webhook acquiringInternetPayment: operationId={}, status={}",
                    event.operationId(), event.status());
            subscriptionPaymentService.completePaymentByOperationId(event.operationId(), event.status());
        } else {
            log.debug("Tochka webhook acknowledged without operationId (test ping)");
        }
        return ResponseEntity.ok().build();
    }
}
