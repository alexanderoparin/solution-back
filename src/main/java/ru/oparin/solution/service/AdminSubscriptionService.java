package ru.oparin.solution.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.oparin.solution.dto.PaymentDto;
import ru.oparin.solution.dto.SubscriptionDto;
import ru.oparin.solution.exception.UserException;
import ru.oparin.solution.model.*;
import ru.oparin.solution.repository.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Админ-действия: ручное продление подписки кабинета / начисление А/Б.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AdminSubscriptionService {

    private final UserRepository userRepository;
    private final PlanRepository planRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final PaymentRepository paymentRepository;
    private final CabinetRepository cabinetRepository;
    private final SubscriptionPaymentService subscriptionPaymentService;
    private final AbTestQuotaService abTestQuotaService;

    /**
     * Назначить/продлить MAIN/CAMPAIGN или начислить AB_PACK кредиты кабинету.
     */
    @Transactional
    public SubscriptionDto extendSubscription(Long userId, Long cabinetId, Long planId, LocalDateTime expiresAt, Integer abCredits) {
        Cabinet cabinet = cabinetRepository.findById(cabinetId)
                .orElseThrow(() -> new UserException("Кабинет не найден", HttpStatus.NOT_FOUND));
        Plan plan = planRepository.findById(planId)
                .orElseThrow(() -> new UserException("План не найден", HttpStatus.NOT_FOUND));
        User owner = cabinet.getUser();
        if (owner == null) {
            throw new UserException("У кабинета нет владельца", HttpStatus.BAD_REQUEST);
        }
        if (userId != null && !owner.getId().equals(userId)) {
            User explicit = userRepository.findById(userId)
                    .orElseThrow(() -> new UserException("Пользователь не найден", HttpStatus.NOT_FOUND));
            // userId в запросе — для совместимости UI; entitlement всегда на кабинет владельца
            log.info("Админ extend: request.userId={} cabinet.ownerId={}", explicit.getId(), owner.getId());
        }

        if (plan.getKind() == PlanKind.AB_PACK) {
            int credits = abCredits != null && abCredits > 0
                    ? abCredits
                    : (plan.getCreditAmount() != null ? plan.getCreditAmount() : 0);
            abTestQuotaService.addCredits(cabinet, credits);
            log.info("Админ начислил {} А/Б кредитов cabinetId={}", credits, cabinetId);
            return SubscriptionDto.builder()
                    .userId(owner.getId())
                    .cabinetId(cabinetId)
                    .planId(plan.getId())
                    .planName(plan.getName())
                    .planCode(plan.getCode())
                    .planKind(plan.getKind().name())
                    .status("credited")
                    .startedAt(LocalDateTime.now())
                    .build();
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime targetExpiresAt = expiresAt != null
                ? expiresAt
                : SubscriptionPeriodUtils.addPlanPeriod(now, plan);

        Subscription current = subscriptionRepository
                .findFirstActiveByCabinetIdAndKind(
                        cabinet.getId(),
                        plan.getKind() != null ? plan.getKind() : PlanKind.CAMPAIGN,
                        List.of("active", "trial"),
                        now)
                .orElse(null);

        Subscription saved;
        if (current != null) {
            current.setExpiresAt(targetExpiresAt);
            current.setPlan(plan);
            current.setStatus("active");
            saved = subscriptionRepository.save(current);
            log.info("Админ продлил подписку {} cabinetId={} до {}", saved.getId(), cabinetId, targetExpiresAt);
        } else {
            saved = subscriptionPaymentService.createOrExtendKindSubscription(owner, cabinet, plan);
            saved.setExpiresAt(targetExpiresAt);
            saved.setStatus("active");
            saved = subscriptionRepository.save(saved);
            log.info("Админ создал подписку {} cabinetId={} до {}", saved.getId(), cabinetId, targetExpiresAt);
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
    public List<SubscriptionDto> getSubscriptionsByCabinetId(Long cabinetId) {
        return subscriptionRepository.findByCabinet_IdOrderByExpiresAtDesc(cabinetId).stream()
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
                .cabinetId(s.getCabinet() != null ? s.getCabinet().getId() : null)
                .planId(planId)
                .planName(planName)
                .planCode(s.getPlan() != null ? s.getPlan().getCode() : null)
                .planKind(s.getPlan() != null && s.getPlan().getKind() != null ? s.getPlan().getKind().name() : null)
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
                .description(p.getDescription())
                .planName(p.getPlanName())
                .status(p.getStatus())
                .paidAt(p.getPaidAt())
                .createdAt(p.getCreatedAt())
                .build();
    }
}
