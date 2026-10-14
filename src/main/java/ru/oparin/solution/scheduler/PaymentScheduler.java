package ru.oparin.solution.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ru.oparin.solution.model.Payment;
import ru.oparin.solution.model.PaymentStatus;
import ru.oparin.solution.repository.PaymentRepository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Планировщик задач для платежей.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentScheduler {

    private final PaymentRepository paymentRepository;

    /**
     * Каждый день в 06:00 помечает как {@link PaymentStatus#EXPIRED} все платежи,
     * которые находятся в ожидании более 6 часов (не оплачены вовремя).
     */
    @Scheduled(cron = "0 0 6 * * ?")
    @Transactional
    public void cancelOldPendingPayments() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime threshold = now.minusHours(6);

        List<Payment> oldPendingPayments =
                paymentRepository.findByStatusAndCreatedAtBefore(PaymentStatus.PENDING.getDbValue(), threshold);

        if (oldPendingPayments.isEmpty()) {
            return;
        }

        log.info("Найдено {} платежей в статусе '{}' старше 6 часов. Помечаем как '{}'.",
                oldPendingPayments.size(), PaymentStatus.PENDING.getDbValue(), PaymentStatus.EXPIRED.getDbValue());

        for (Payment payment : oldPendingPayments) {
            payment.setStatus(PaymentStatus.EXPIRED.getDbValue());
        }

        paymentRepository.saveAll(oldPendingPayments);
    }
}

