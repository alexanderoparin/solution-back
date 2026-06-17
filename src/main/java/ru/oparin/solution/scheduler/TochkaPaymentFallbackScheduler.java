package ru.oparin.solution.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ru.oparin.solution.config.TochkaProperties;
import ru.oparin.solution.model.Payment;
import ru.oparin.solution.model.PaymentStatus;
import ru.oparin.solution.repository.PaymentRepository;
import ru.oparin.solution.service.SubscriptionPaymentService;
import ru.oparin.solution.service.tochka.TochkaAcquiringService;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Fallback-опрос Точка.API, если webhook не завершил платёж вовремя.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TochkaPaymentFallbackScheduler {

    /** Сколько ждём webhook, прежде чем опрашивать Точка. */
    private static final int WEBHOOK_WAIT_SECONDS = 45;

    private final PaymentRepository paymentRepository;
    private final TochkaProperties tochkaProperties;
    private final TochkaAcquiringService tochkaAcquiringService;
    private final SubscriptionPaymentService subscriptionPaymentService;

    /**
     * Каждые 30 с проверяет pending-платежи старше {@link #WEBHOOK_WAIT_SECONDS} с operationId.
     */
    @Scheduled(fixedDelay = 30_000)
    public void pollPendingPaymentsAfterWebhookTimeout() {
        if (!tochkaProperties.isConfiguredForPayments()) {
            return;
        }

        LocalDateTime createdBefore = LocalDateTime.now().minusSeconds(WEBHOOK_WAIT_SECONDS);
        List<Payment> pending = paymentRepository.findByStatusAndExternalIdIsNotNullAndCreatedAtBefore(
                PaymentStatus.PENDING.getDbValue(), createdBefore);

        for (Payment payment : pending) {
            pollPayment(payment);
        }
    }

    private void pollPayment(Payment payment) {
        String operationId = payment.getExternalId();
        if (operationId == null || operationId.isBlank()) {
            return;
        }
        try {
            var remote = tochkaAcquiringService.fetchOperationStatus(operationId);
            if (remote.getStatus() == null) {
                return;
            }
            log.debug("Tochka fallback poll paymentId={} status={}", payment.getId(), remote.getStatus());
            subscriptionPaymentService.completePaymentByOperationId(operationId, remote.getStatus());
        } catch (Exception e) {
            log.debug("Tochka fallback poll skipped for paymentId={}: {}", payment.getId(), e.getMessage());
        }
    }
}
