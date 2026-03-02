package ru.oparin.solution.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.oparin.solution.dto.PaymentDto;
import ru.oparin.solution.dto.SubscriptionDto;
import ru.oparin.solution.exception.UserException;
import ru.oparin.solution.model.Payment;
import ru.oparin.solution.model.Plan;
import ru.oparin.solution.model.Subscription;
import ru.oparin.solution.model.User;
import ru.oparin.solution.repository.PaymentRepository;
import ru.oparin.solution.repository.PlanRepository;
import ru.oparin.solution.repository.SubscriptionRepository;
import ru.oparin.solution.repository.UserRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Сервис админ-действий: ручное продление подписки и т.д.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AdminSubscriptionService {

    private final UserRepository userRepository;
    private final PlanRepository planRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final PaymentRepository paymentRepository;

    /**
     * Назначить или продлить подписку пользователю (ручное действие админа).
     * Если у пользователя уже есть активная подписка — продлевает её до expiresAt (или до now + periodDays).
     * Иначе создаёт новую подписку.
     */
    @Transactional
    public SubscriptionDto extendSubscription(Long userId, Long planId, LocalDateTime expiresAt) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserException("Пользователь не найден", HttpStatus.NOT_FOUND));
        Plan plan = planRepository.findById(planId)
                .orElseThrow(() -> new UserException("План не найден", HttpStatus.NOT_FOUND));

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime targetExpiresAt = expiresAt != null ? expiresAt : now.plusDays(plan.getPeriodDays());

        Subscription current = subscriptionRepository
                .findFirstByUser_IdAndStatusInAndExpiresAtAfterOrderByExpiresAtDesc(
                        user.getId(), List.of("active", "trial"), now
                )
                .orElse(null);

        Subscription saved;
        if (current != null && current.getExpiresAt().isAfter(now)) {
            current.setExpiresAt(targetExpiresAt);
            current.setPlan(plan);
            current.setStatus("active");
            saved = subscriptionRepository.save(current);
            log.info("Админ продлил подписку {} пользователя {} до {}", saved.getId(), userId, targetExpiresAt);
        } else {
            Subscription subscription = Subscription.builder()
                    .user(user)
                    .plan(plan)
                    .status("active")
                    .startedAt(now)
                    .expiresAt(targetExpiresAt)
                    .build();
            saved = subscriptionRepository.save(subscription);
            log.info("Админ создал подписку {} для пользователя {} до {}", saved.getId(), userId, targetExpiresAt);
        }
        return toSubscriptionDto(saved);
    }

    @Transactional(readOnly = true)
    public List<SubscriptionDto> getSubscriptionsByUserId(Long userId) {
        return subscriptionRepository.findByUser_IdOrderByExpiresAtDesc(userId).stream()
                .map(this::toSubscriptionDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<PaymentDto> getPaymentsByUserId(Long userId) {
        return paymentRepository.findByUser_IdOrderByCreatedAtDesc(userId).stream()
                .map(this::toPaymentDto)
                .collect(Collectors.toList());
    }

    private SubscriptionDto toSubscriptionDto(Subscription s) {
        String planName = s.getPlan() != null ? s.getPlan().getName() : null;
        Long planId = s.getPlan() != null ? s.getPlan().getId() : null;
        return SubscriptionDto.builder()
                .id(s.getId())
                .userId(s.getUser().getId())
                .planId(planId)
                .planName(planName)
                .status(s.getStatus())
                .startedAt(s.getStartedAt())
                .expiresAt(s.getExpiresAt())
                .createdAt(s.getCreatedAt())
                .build();
    }

    private PaymentDto toPaymentDto(Payment p) {
        return PaymentDto.builder()
                .id(p.getId())
                .amount(p.getAmount())
                .currency(p.getCurrency())
                .status(p.getStatus())
                .paidAt(p.getPaidAt())
                .createdAt(p.getCreatedAt())
                .build();
    }
}
