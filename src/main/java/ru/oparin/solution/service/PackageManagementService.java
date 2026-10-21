package ru.oparin.solution.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.oparin.solution.config.TochkaProperties;
import ru.oparin.solution.dto.*;
import ru.oparin.solution.exception.UserException;
import ru.oparin.solution.model.*;
import ru.oparin.solution.repository.*;
import ru.oparin.solution.service.tochka.TochkaAcquiringService;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Добор кабинетов в оплаченный пакет и апгрейд тира с prorate-доплатой.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PackageManagementService {

    private static final List<String> ACTIVE_STATUSES = List.of("active", "trial");

    private final SubscriptionRepository subscriptionRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentCabinetRepository paymentCabinetRepository;
    private final CabinetRepository cabinetRepository;
    private final PlanRepository planRepository;
    private final TochkaAcquiringService tochkaAcquiringService;
    private final TochkaProperties tochkaProperties;
    private final ObjectMapper objectMapper;

    @Value("${app.brand-name:Clicki}")
    private String brandName;

    /**
     * Добавляет кабинеты в пакет без оплаты (слоты уже оплачены до max плана).
     */
    @Transactional
    public MessageResponse addCabinets(User user, AddPackageCabinetsRequest request) {
        validateSeller(user);
        Payment payment = requireOwnedPayment(user, request.getPaymentId());
        List<Subscription> group = activeGroupByPayment(payment.getId());
        if (group.isEmpty()) {
            throw new UserException("Активный пакет по этому платежу не найден", HttpStatus.NOT_FOUND);
        }
        Plan plan = group.get(0).getPlan();
        if (plan == null || plan.getKind() == PlanKind.AB_PACK) {
            throw new UserException("Добор кабинетов недоступен для этого пакета", HttpStatus.BAD_REQUEST);
        }
        int max = plan.getMaxCabinets() != null ? plan.getMaxCabinets() : 1;
        int used = group.size();
        int freeSlots = max - used;
        if (freeSlots <= 0) {
            throw new UserException("В пакете нет свободных слотов", HttpStatus.BAD_REQUEST);
        }

        Set<Long> uniqueIds = new LinkedHashSet<>(request.getCabinetIds());
        if (uniqueIds.size() > freeSlots) {
            throw new UserException(
                    "Можно добавить не больше " + freeSlots + " кабинетов",
                    HttpStatus.BAD_REQUEST);
        }

        Set<Long> alreadyInPackage = new LinkedHashSet<>();
        for (Subscription s : group) {
            if (s.getCabinet() != null) {
                alreadyInPackage.add(s.getCabinet().getId());
            }
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiresAt = group.stream()
                .map(Subscription::getExpiresAt)
                .filter(e -> e != null)
                .min(Comparator.naturalOrder())
                .orElse(null);
        PlanKind kind = plan.getKind();

        int added = 0;
        for (Long cabinetId : uniqueIds) {
            if (alreadyInPackage.contains(cabinetId)) {
                throw new UserException("Кабинет " + cabinetId + " уже в этом пакете", HttpStatus.BAD_REQUEST);
            }
            Cabinet cabinet = requireOwnedCabinet(user, cabinetId);
            subscriptionRepository
                    .findFirstActiveByCabinetIdAndKind(cabinetId, kind, ACTIVE_STATUSES, now)
                    .ifPresent(s -> {
                        throw new UserException(
                                "У кабинета «" + cabinet.getName() + "» уже есть активная подписка этого типа",
                                HttpStatus.BAD_REQUEST);
                    });

            subscriptionRepository.save(Subscription.builder()
                    .user(user)
                    .cabinet(cabinet)
                    .plan(plan)
                    .payment(payment)
                    .status("active")
                    .startedAt(now)
                    .expiresAt(expiresAt)
                    .autoRenew(false)
                    .build());

            paymentCabinetRepository.save(PaymentCabinet.builder()
                    .paymentId(payment.getId())
                    .cabinetId(cabinetId)
                    .payment(payment)
                    .cabinet(cabinet)
                    .build());
            added++;
        }

        log.info("В пакет paymentId={} добавлено {} кабинетов (userId={})",
                payment.getId(), added, user.getId());
        return MessageResponse.builder()
                .message("Добавлено кабинетов: " + added)
                .build();
    }

    /**
     * Расчёт доплаты за апгрейд тира вверх (срок не меняется).
     */
    @Transactional(readOnly = true)
    public UpgradePackageQuoteDto quoteUpgrade(User user, Long paymentId, Long targetPlanId) {
        validateSeller(user);
        Payment payment = requireOwnedPayment(user, paymentId);
        List<Subscription> group = activeGroupByPayment(payment.getId());
        if (group.isEmpty()) {
            throw new UserException("Активный пакет не найден", HttpStatus.NOT_FOUND);
        }
        Plan current = group.get(0).getPlan();
        Plan target = planRepository.findById(targetPlanId)
                .orElseThrow(() -> new UserException("План не найден", HttpStatus.NOT_FOUND));
        validateUpgradeTarget(current, target);

        LocalDateTime expiresAt = group.stream()
                .map(Subscription::getExpiresAt)
                .filter(e -> e != null)
                .min(Comparator.naturalOrder())
                .orElse(null);
        if (expiresAt == null) {
            throw new UserException("У пакета нет срока окончания — апгрейд недоступен", HttpStatus.BAD_REQUEST);
        }

        LocalDateTime now = LocalDateTime.now();
        long remainingDays = Math.max(0, ChronoUnit.DAYS.between(now.toLocalDate(), expiresAt.toLocalDate()));
        long periodDays = resolvePeriodDays(current, group.get(0).getStartedAt(), expiresAt);
        BigDecimal currentPrice = current.getPriceRub() != null ? current.getPriceRub() : BigDecimal.ZERO;
        BigDecimal targetPrice = target.getPriceRub() != null ? target.getPriceRub() : BigDecimal.ZERO;
        BigDecimal diff = targetPrice.subtract(currentPrice);
        if (diff.compareTo(BigDecimal.ZERO) <= 0) {
            throw new UserException("Целевой тариф не дороже текущего", HttpStatus.BAD_REQUEST);
        }
        BigDecimal amount = diff
                .multiply(BigDecimal.valueOf(remainingDays))
                .divide(BigDecimal.valueOf(Math.max(periodDays, 1)), 0, RoundingMode.HALF_UP);
        if (remainingDays > 0 && amount.compareTo(BigDecimal.ZERO) == 0) {
            amount = BigDecimal.ONE;
        }

        return UpgradePackageQuoteDto.builder()
                .paymentId(paymentId)
                .currentPlanId(current.getId())
                .currentPlanCode(current.getCode())
                .targetPlanId(target.getId())
                .targetPlanCode(target.getCode())
                .targetPlanName(target.getName())
                .currentPriceRub(currentPrice)
                .targetPriceRub(targetPrice)
                .priceDiffRub(diff)
                .remainingDays(remainingDays)
                .periodDays(periodDays)
                .amountRub(amount)
                .currentMaxCabinets(current.getMaxCabinets())
                .targetMaxCabinets(target.getMaxCabinets())
                .build();
    }

    /**
     * Инициация оплаты апгрейда тира.
     */
    @Transactional
    public InitiatePaymentResponse initiateUpgrade(User user, UpgradePackageRequest request) {
        validateSeller(user);
        if (!tochkaProperties.isConfiguredForPayments()) {
            throw new UserException("Оплата временно недоступна", HttpStatus.SERVICE_UNAVAILABLE);
        }
        UpgradePackageQuoteDto quote = quoteUpgrade(user, request.getPaymentId(), request.getTargetPlanId());
        if (quote.getAmountRub() == null || quote.getAmountRub().compareTo(BigDecimal.ZERO) <= 0) {
            throw new UserException("Доплата равна нулю", HttpStatus.BAD_REQUEST);
        }

        Plan target = planRepository.findById(request.getTargetPlanId())
                .orElseThrow(() -> new UserException("План не найден", HttpStatus.NOT_FOUND));
        List<Subscription> group = activeGroupByPayment(request.getPaymentId());
        Cabinet firstCabinet = group.get(0).getCabinet();
        LocalDateTime expiresAt = group.stream()
                .map(Subscription::getExpiresAt)
                .filter(e -> e != null)
                .min(Comparator.naturalOrder())
                .orElseThrow();

        List<Long> addCabinetIds = resolveUpgradeAddCabinetIds(user, group, target, request.getCabinetIds());

        Payment payment = Payment.builder()
                .user(user)
                .cabinet(firstCabinet)
                .planCode(target.getCode())
                .planName(target.getName())
                .periodDays(target.getPeriodDays())
                .periodType(target.getPeriodType())
                .amount(quote.getAmountRub())
                .currency("RUB")
                .status(PaymentStatus.PENDING.getDbValue())
                .description("Апгрейд тарифа: " + target.getName())
                .build();
        payment = paymentRepository.save(payment);

        for (Subscription s : group) {
            if (s.getCabinet() == null) {
                continue;
            }
            paymentCabinetRepository.save(PaymentCabinet.builder()
                    .paymentId(payment.getId())
                    .cabinetId(s.getCabinet().getId())
                    .payment(payment)
                    .cabinet(s.getCabinet())
                    .build());
        }

        payment.setMetadata(buildUpgradeMetadata(
                request.getPaymentId(),
                target.getId(),
                expiresAt,
                quote.getAmountRub(),
                addCabinetIds,
                false
        ));
        payment = paymentRepository.save(payment);

        var tochkaResult = tochkaAcquiringService.createSubscriptionPayment(
                user, target, payment.getId(), brandName, quote.getAmountRub());
        payment.setExternalId(tochkaResult.getOperationId());
        payment.setMetadata(mergeTochkaIntoMetadata(
                payment.getMetadata(),
                tochkaResult.getOperationId(),
                tochkaResult.getPaymentLink(),
                tochkaResult.getStatus()
        ));
        paymentRepository.save(payment);

        log.info("Создан платёж апгрейда id={} sourcePaymentId={} amount={}",
                payment.getId(), request.getPaymentId(), quote.getAmountRub());

        return InitiatePaymentResponse.builder()
                .paymentId(payment.getId())
                .paymentUrl(tochkaResult.getPaymentLink())
                .build();
    }

    /**
     * Есть ли парный PRO для CAMPAIGN-плана (тот же max и срок).
     */
    @Transactional(readOnly = true)
    public boolean canUpgradeToPro(Plan campaignPlan) {
        return resolveProCounterpart(campaignPlan).isPresent();
    }

    /**
     * Парный PRO: тот же maxCabinets и periodType (/ periodDays для DAYS).
     */
    @Transactional(readOnly = true)
    public java.util.Optional<Plan> resolveProCounterpart(Plan campaignPlan) {
        if (campaignPlan == null || campaignPlan.getKind() != PlanKind.CAMPAIGN) {
            return java.util.Optional.empty();
        }
        int max = campaignPlan.getMaxCabinets() != null ? campaignPlan.getMaxCabinets() : 1;
        return planRepository.findByIsActiveTrueAndKindOrderBySortOrderAsc(PlanKind.MAIN).stream()
                .filter(p -> PlanCodes.isProCode(p.getCode()))
                .filter(p -> p.getPeriodType() == campaignPlan.getPeriodType())
                .filter(p -> {
                    if (campaignPlan.getPeriodType() == PlanPeriodType.DAYS) {
                        return java.util.Objects.equals(p.getPeriodDays(), campaignPlan.getPeriodDays());
                    }
                    return true;
                })
                .filter(p -> (p.getMaxCabinets() != null ? p.getMaxCabinets() : 1) == max)
                .findFirst();
    }

    /**
     * Расчёт доплаты CAMPAIGN → PRO (срок не меняется).
     */
    @Transactional(readOnly = true)
    public UpgradePackageQuoteDto quoteUpgradeToPro(User user, Long paymentId) {
        validateSeller(user);
        Payment payment = requireOwnedPayment(user, paymentId);
        List<Subscription> group = activeGroupByPayment(payment.getId());
        if (group.isEmpty()) {
            throw new UserException("Активный пакет не найден", HttpStatus.NOT_FOUND);
        }
        Plan current = group.get(0).getPlan();
        if (current == null || current.getKind() != PlanKind.CAMPAIGN) {
            throw new UserException("Улучшить до PRO можно только пакет «Управление РК»", HttpStatus.BAD_REQUEST);
        }
        Plan target = resolveProCounterpart(current)
                .orElseThrow(() -> new UserException("Нет подходящего тарифа PRO", HttpStatus.BAD_REQUEST));

        LocalDateTime expiresAt = group.stream()
                .map(Subscription::getExpiresAt)
                .filter(e -> e != null)
                .min(Comparator.naturalOrder())
                .orElse(null);
        if (expiresAt == null) {
            throw new UserException("У пакета нет срока окончания — апгрейд недоступен", HttpStatus.BAD_REQUEST);
        }

        LocalDateTime now = LocalDateTime.now();
        long remainingDays = Math.max(0, ChronoUnit.DAYS.between(now.toLocalDate(), expiresAt.toLocalDate()));
        long periodDays = resolvePeriodDays(current, group.get(0).getStartedAt(), expiresAt);
        BigDecimal currentPrice = current.getPriceRub() != null ? current.getPriceRub() : BigDecimal.ZERO;
        BigDecimal targetPrice = target.getPriceRub() != null ? target.getPriceRub() : BigDecimal.ZERO;
        BigDecimal diff = targetPrice.subtract(currentPrice);
        if (diff.compareTo(BigDecimal.ZERO) <= 0) {
            throw new UserException("Тариф PRO не дороже текущего", HttpStatus.BAD_REQUEST);
        }
        BigDecimal amount = diff
                .multiply(BigDecimal.valueOf(remainingDays))
                .divide(BigDecimal.valueOf(Math.max(periodDays, 1)), 0, RoundingMode.HALF_UP);
        if (remainingDays > 0 && amount.compareTo(BigDecimal.ZERO) == 0) {
            amount = BigDecimal.ONE;
        }

        return UpgradePackageQuoteDto.builder()
                .paymentId(paymentId)
                .currentPlanId(current.getId())
                .currentPlanCode(current.getCode())
                .targetPlanId(target.getId())
                .targetPlanCode(target.getCode())
                .targetPlanName(target.getName())
                .currentPriceRub(currentPrice)
                .targetPriceRub(targetPrice)
                .priceDiffRub(diff)
                .remainingDays(remainingDays)
                .periodDays(periodDays)
                .amountRub(amount)
                .currentMaxCabinets(current.getMaxCabinets())
                .targetMaxCabinets(target.getMaxCabinets())
                .build();
    }

    /**
     * Инициация оплаты апгрейда CAMPAIGN → PRO.
     */
    @Transactional
    public InitiatePaymentResponse initiateUpgradeToPro(User user, UpgradeToProRequest request) {
        validateSeller(user);
        if (!tochkaProperties.isConfiguredForPayments()) {
            throw new UserException("Оплата временно недоступна", HttpStatus.SERVICE_UNAVAILABLE);
        }
        UpgradePackageQuoteDto quote = quoteUpgradeToPro(user, request.getPaymentId());
        if (quote.getAmountRub() == null || quote.getAmountRub().compareTo(BigDecimal.ZERO) <= 0) {
            throw new UserException("Доплата равна нулю", HttpStatus.BAD_REQUEST);
        }

        Plan target = planRepository.findById(quote.getTargetPlanId())
                .orElseThrow(() -> new UserException("План не найден", HttpStatus.NOT_FOUND));
        List<Subscription> group = activeGroupByPayment(request.getPaymentId());
        LocalDateTime now = LocalDateTime.now();
        for (Subscription s : group) {
            if (s.getCabinet() == null) {
                continue;
            }
            subscriptionRepository
                    .findFirstActiveByCabinetIdAndKind(s.getCabinet().getId(), PlanKind.MAIN, ACTIVE_STATUSES, now)
                    .ifPresent(main -> {
                        if (main.getPlan() != null && PlanCodes.isProCode(main.getPlan().getCode())) {
                            throw new UserException(
                                    "У кабинета «" + s.getCabinet().getName()
                                            + "» уже есть полный доступ — улучшение до PRO недоступно",
                                    HttpStatus.BAD_REQUEST);
                        }
                    });
        }

        Cabinet firstCabinet = group.get(0).getCabinet();
        LocalDateTime expiresAt = group.stream()
                .map(Subscription::getExpiresAt)
                .filter(e -> e != null)
                .min(Comparator.naturalOrder())
                .orElseThrow();

        Payment payment = Payment.builder()
                .user(user)
                .cabinet(firstCabinet)
                .planCode(target.getCode())
                .planName(target.getName())
                .periodDays(target.getPeriodDays())
                .periodType(target.getPeriodType())
                .amount(quote.getAmountRub())
                .currency("RUB")
                .status(PaymentStatus.PENDING.getDbValue())
                .description("Улучшение до PRO: " + target.getName())
                .build();
        payment = paymentRepository.save(payment);

        for (Subscription s : group) {
            if (s.getCabinet() == null) {
                continue;
            }
            paymentCabinetRepository.save(PaymentCabinet.builder()
                    .paymentId(payment.getId())
                    .cabinetId(s.getCabinet().getId())
                    .payment(payment)
                    .cabinet(s.getCabinet())
                    .build());
        }

        payment.setMetadata(buildUpgradeMetadata(
                request.getPaymentId(),
                target.getId(),
                expiresAt,
                quote.getAmountRub(),
                List.of(),
                true
        ));
        payment = paymentRepository.save(payment);

        var tochkaResult = tochkaAcquiringService.createSubscriptionPayment(
                user, target, payment.getId(), brandName, quote.getAmountRub());
        payment.setExternalId(tochkaResult.getOperationId());
        payment.setMetadata(mergeTochkaIntoMetadata(
                payment.getMetadata(),
                tochkaResult.getOperationId(),
                tochkaResult.getPaymentLink(),
                tochkaResult.getStatus()
        ));
        paymentRepository.save(payment);

        log.info("Создан платёж апгрейда до PRO id={} sourcePaymentId={} amount={}",
                payment.getId(), request.getPaymentId(), quote.getAmountRub());

        return InitiatePaymentResponse.builder()
                .paymentId(payment.getId())
                .paymentUrl(tochkaResult.getPaymentLink())
                .build();
    }

    /**
     * Применяет успешный платёж-апгрейд: меняет plan у подписок пакета, срок сохраняет.
     * Для upgradeToPro — CAMPAIGN → MAIN PRO, бесплатный MAIN на кабинетах гасится.
     *
     * @return true если платёж был апгрейдом и обработан
     */
    @Transactional
    public boolean applyUpgradeIfNeeded(Payment payment) {
        Long sourcePaymentId = readLongMeta(payment.getMetadata(), "upgradeSourcePaymentId");
        Long targetPlanId = readLongMeta(payment.getMetadata(), "upgradeTargetPlanId");
        if (sourcePaymentId == null || targetPlanId == null) {
            return false;
        }
        Plan target = planRepository.findById(targetPlanId).orElse(null);
        if (target == null) {
            log.error("Upgrade payment id={}: target plan {} missing", payment.getId(), targetPlanId);
            return true;
        }
        boolean upgradeToPro = readBooleanMeta(payment.getMetadata(), "upgradeToPro");
        PlanKind expectedSourceKind = upgradeToPro ? PlanKind.CAMPAIGN : target.getKind();

        List<Subscription> group = subscriptionRepository.findByPayment_Id(sourcePaymentId);
        LocalDateTime keepExpires = readDateTimeMeta(payment.getMetadata(), "keepExpiresAt");
        LocalDateTime now = LocalDateTime.now();
        Subscription first = null;
        for (Subscription s : group) {
            if (s.getPlan() == null || s.getPlan().getKind() != expectedSourceKind) {
                continue;
            }
            if (!ACTIVE_STATUSES.contains(s.getStatus())) {
                continue;
            }
            if (upgradeToPro && s.getCabinet() != null) {
                expireFreeMainOnCabinet(s.getCabinet(), now);
                boolean hasPaidPro = subscriptionRepository
                        .findFirstActiveByCabinetIdAndKind(s.getCabinet().getId(), PlanKind.MAIN, ACTIVE_STATUSES, now)
                        .filter(m -> m.getPlan() != null && PlanCodes.isProCode(m.getPlan().getCode()))
                        .isPresent();
                if (hasPaidPro) {
                    log.warn("Upgrade-to-PRO paymentId={}: кабинет {} уже с PRO, CAMPAIGN-подписка {} пропущена",
                            payment.getId(), s.getCabinet().getId(), s.getId());
                    s.setStatus("expired");
                    subscriptionRepository.save(s);
                    continue;
                }
            }
            s.setPlan(target);
            s.setPayment(payment);
            if (keepExpires != null) {
                s.setExpiresAt(keepExpires);
            }
            s.setStatus("active");
            subscriptionRepository.save(s);
            if (first == null) {
                first = s;
            }
        }
        if (first != null) {
            payment.setSubscription(first);
        }

        List<Long> addCabinetIds = readLongListMeta(payment.getMetadata(), "upgradeAddCabinetIds");
        if (!addCabinetIds.isEmpty() && payment.getUser() != null) {
            addCabinetsAfterUpgrade(payment, target, keepExpires, addCabinetIds);
        }

        log.info("Апгрейд применён: paymentId={} sourcePaymentId={} → plan {} upgradeToPro={}",
                payment.getId(), sourcePaymentId, target.getCode(), upgradeToPro);
        return true;
    }

    /** Гасит бесплатный MAIN (analytics_free) на кабинете перед переходом на PRO. */
    private void expireFreeMainOnCabinet(Cabinet cabinet, LocalDateTime now) {
        subscriptionRepository
                .findFirstActiveByCabinetIdAndKind(cabinet.getId(), PlanKind.MAIN, ACTIVE_STATUSES, now)
                .ifPresent(main -> {
                    if (main.getPlan() != null && PlanCodes.ANALYTICS_FREE.equals(main.getPlan().getCode())) {
                        main.setStatus("expired");
                        if (main.getExpiresAt() == null || main.getExpiresAt().isAfter(now)) {
                            main.setExpiresAt(now);
                        }
                        subscriptionRepository.save(main);
                    }
                });
    }

    /**
     * Добор кабинетов, выбранных при инициации апгрейда (после смены плана).
     */
    private void addCabinetsAfterUpgrade(
            Payment payment,
            Plan target,
            LocalDateTime expiresAt,
            List<Long> cabinetIds
    ) {
        User user = payment.getUser();
        LocalDateTime now = LocalDateTime.now();
        PlanKind kind = target.getKind();
        Set<Long> already = subscriptionRepository.findByPayment_Id(payment.getId()).stream()
                .filter(s -> s.getCabinet() != null)
                .map(s -> s.getCabinet().getId())
                .collect(Collectors.toCollection(LinkedHashSet::new));

        int added = 0;
        for (Long cabinetId : cabinetIds) {
            if (already.contains(cabinetId)) {
                continue;
            }
            Cabinet cabinet = requireOwnedCabinet(user, cabinetId);
            boolean conflict = subscriptionRepository
                    .findFirstActiveByCabinetIdAndKind(cabinetId, kind, ACTIVE_STATUSES, now)
                    .isPresent();
            if (conflict) {
                log.warn("Upgrade paymentId={}: кабинет {} уже имеет активную подписку {}, пропуск",
                        payment.getId(), cabinetId, kind);
                continue;
            }
            subscriptionRepository.save(Subscription.builder()
                    .user(user)
                    .cabinet(cabinet)
                    .plan(target)
                    .payment(payment)
                    .status("active")
                    .startedAt(now)
                    .expiresAt(expiresAt)
                    .autoRenew(false)
                    .build());
            paymentCabinetRepository.save(PaymentCabinet.builder()
                    .paymentId(payment.getId())
                    .cabinetId(cabinetId)
                    .payment(payment)
                    .cabinet(cabinet)
                    .build());
            already.add(cabinetId);
            added++;
        }
        if (added > 0) {
            log.info("После апгрейда paymentId={} добавлено {} кабинетов", payment.getId(), added);
        }
    }

    /**
     * Валидация кабинетов для добора в рамках апгрейда (слоты целевого тира).
     */
    private List<Long> resolveUpgradeAddCabinetIds(
            User user,
            List<Subscription> group,
            Plan target,
            List<Long> requested
    ) {
        if (requested == null || requested.isEmpty()) {
            return List.of();
        }
        Set<Long> uniqueIds = new LinkedHashSet<>(requested);
        Set<Long> alreadyInPackage = new LinkedHashSet<>();
        for (Subscription s : group) {
            if (s.getCabinet() != null) {
                alreadyInPackage.add(s.getCabinet().getId());
            }
        }
        int targetMax = target.getMaxCabinets() != null ? target.getMaxCabinets() : 1;
        int freeSlots = Math.max(0, targetMax - alreadyInPackage.size());
        if (uniqueIds.size() > freeSlots) {
            throw new UserException(
                    "Можно добавить не больше " + freeSlots + " кабинетов",
                    HttpStatus.BAD_REQUEST);
        }
        LocalDateTime now = LocalDateTime.now();
        PlanKind kind = target.getKind();
        for (Long cabinetId : uniqueIds) {
            if (alreadyInPackage.contains(cabinetId)) {
                throw new UserException("Кабинет " + cabinetId + " уже в этом пакете", HttpStatus.BAD_REQUEST);
            }
            Cabinet cabinet = requireOwnedCabinet(user, cabinetId);
            subscriptionRepository
                    .findFirstActiveByCabinetIdAndKind(cabinetId, kind, ACTIVE_STATUSES, now)
                    .ifPresent(s -> {
                        throw new UserException(
                                "У кабинета «" + cabinet.getName() + "» уже есть активная подписка этого типа",
                                HttpStatus.BAD_REQUEST);
                    });
        }
        return new ArrayList<>(uniqueIds);
    }

    /**
     * Планы апгрейда вверх для текущего плана (тот же kind и periodType, больший max).
     */
    @Transactional(readOnly = true)
    public List<Plan> listUpgradeTargets(Plan current) {
        if (current == null || current.getKind() == null || current.getKind() == PlanKind.AB_PACK) {
            return List.of();
        }
        int currentMax = current.getMaxCabinets() != null ? current.getMaxCabinets() : 1;
        return planRepository.findByIsActiveTrueAndKindOrderBySortOrderAsc(current.getKind()).stream()
                .filter(p -> p.getPeriodType() == current.getPeriodType())
                .filter(p -> {
                    if (current.getPeriodType() == PlanPeriodType.DAYS) {
                        return java.util.Objects.equals(p.getPeriodDays(), current.getPeriodDays());
                    }
                    return true;
                })
                .filter(p -> (p.getMaxCabinets() != null ? p.getMaxCabinets() : 1) > currentMax)
                .sorted(Comparator.comparing(p -> p.getMaxCabinets() != null ? p.getMaxCabinets() : 0))
                .toList();
    }

    /**
     * Планы апгрейда для пакета пользователя.
     */
    @Transactional(readOnly = true)
    public List<Plan> listUpgradeTargetsForPayment(User user, Long paymentId) {
        requireOwnedPayment(user, paymentId);
        List<Subscription> group = activeGroupByPayment(paymentId);
        if (group.isEmpty() || group.get(0).getPlan() == null) {
            return List.of();
        }
        return listUpgradeTargets(group.get(0).getPlan());
    }

    private void validateUpgradeTarget(Plan current, Plan target) {
        if (current == null || target == null) {
            throw new UserException("План не найден", HttpStatus.BAD_REQUEST);
        }
        if (!Boolean.TRUE.equals(target.getIsActive())) {
            throw new UserException("Целевой план недоступен", HttpStatus.BAD_REQUEST);
        }
        if (current.getKind() != target.getKind()) {
            throw new UserException("Тип тарифа должен совпадать", HttpStatus.BAD_REQUEST);
        }
        if (current.getPeriodType() != target.getPeriodType()) {
            throw new UserException("Срок тарифа менять нельзя", HttpStatus.BAD_REQUEST);
        }
        if (current.getPeriodType() == PlanPeriodType.DAYS
                && !java.util.Objects.equals(current.getPeriodDays(), target.getPeriodDays())) {
            throw new UserException("Срок тарифа менять нельзя", HttpStatus.BAD_REQUEST);
        }
        int curMax = current.getMaxCabinets() != null ? current.getMaxCabinets() : 1;
        int tgtMax = target.getMaxCabinets() != null ? target.getMaxCabinets() : 1;
        if (tgtMax <= curMax) {
            throw new UserException("Апгрейд только на больший пакет кабинетов", HttpStatus.BAD_REQUEST);
        }
    }

    private long resolvePeriodDays(Plan plan, LocalDateTime startedAt, LocalDateTime expiresAt) {
        if (plan.getPeriodType() == PlanPeriodType.CALENDAR_MONTH) {
            if (startedAt != null && expiresAt != null) {
                long days = ChronoUnit.DAYS.between(startedAt.toLocalDate(), expiresAt.toLocalDate());
                if (days > 0) {
                    return days;
                }
            }
            return 30;
        }
        return plan.getPeriodDays() != null && plan.getPeriodDays() > 0 ? plan.getPeriodDays() : 14;
    }

    private List<Subscription> activeGroupByPayment(Long paymentId) {
        LocalDateTime now = LocalDateTime.now();
        List<Subscription> all = subscriptionRepository.findByPayment_Id(paymentId);
        List<Subscription> active = new ArrayList<>();
        for (Subscription s : all) {
            if (!ACTIVE_STATUSES.contains(s.getStatus())) {
                continue;
            }
            if (s.getExpiresAt() != null && !s.getExpiresAt().isAfter(now)) {
                continue;
            }
            active.add(s);
        }
        return active;
    }

    private Payment requireOwnedPayment(User user, Long paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new UserException("Платёж не найден", HttpStatus.NOT_FOUND));
        if (user.getRole() != Role.ADMIN
                && (payment.getUser() == null || !payment.getUser().getId().equals(user.getId()))) {
            throw new UserException("Нет доступа к пакету", HttpStatus.FORBIDDEN);
        }
        return payment;
    }

    private Cabinet requireOwnedCabinet(User user, Long cabinetId) {
        Cabinet cabinet = cabinetRepository.findById(cabinetId)
                .orElseThrow(() -> new UserException("Кабинет не найден", HttpStatus.NOT_FOUND));
        if (user.getRole() != Role.ADMIN
                && (cabinet.getUser() == null || !cabinet.getUser().getId().equals(user.getId()))) {
            throw new UserException("Кабинет вам не принадлежит", HttpStatus.FORBIDDEN);
        }
        return cabinet;
    }

    private void validateSeller(User user) {
        if (user.getRole() != Role.USER && user.getRole() != Role.ADMIN) {
            throw new UserException("Недостаточно прав", HttpStatus.FORBIDDEN);
        }
        if (user.getRole() == Role.USER && !Boolean.TRUE.equals(user.getEmailConfirmed())) {
            throw new UserException("Подтвердите почту", HttpStatus.FORBIDDEN);
        }
    }

    private String buildUpgradeMetadata(
            Long sourcePaymentId,
            Long targetPlanId,
            LocalDateTime keepExpiresAt,
            BigDecimal amount,
            List<Long> addCabinetIds,
            boolean upgradeToPro
    ) {
        try {
            ObjectNode node = objectMapper.createObjectNode();
            node.put("upgrade", true);
            node.put("upgradeSourcePaymentId", sourcePaymentId);
            node.put("upgradeTargetPlanId", targetPlanId);
            node.put("keepExpiresAt", keepExpiresAt.toString());
            node.put("upgradeAmountRub", amount);
            if (upgradeToPro) {
                node.put("upgradeToPro", true);
            }
            if (addCabinetIds != null && !addCabinetIds.isEmpty()) {
                var arr = node.putArray("upgradeAddCabinetIds");
                for (Long id : addCabinetIds) {
                    arr.add(id);
                }
            }
            return objectMapper.writeValueAsString(node);
        } catch (Exception e) {
            throw new UserException("Не удалось подготовить платёж", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private boolean readBooleanMeta(String metadata, String field) {
        try {
            if (metadata == null || metadata.isBlank()) {
                return false;
            }
            JsonNode node = objectMapper.readTree(metadata).get(field);
            return node != null && node.isBoolean() && node.booleanValue();
        } catch (Exception e) {
            return false;
        }
    }

    private List<Long> readLongListMeta(String metadata, String field) {
        try {
            if (metadata == null || metadata.isBlank()) {
                return List.of();
            }
            JsonNode node = objectMapper.readTree(metadata).get(field);
            if (node == null || !node.isArray()) {
                return List.of();
            }
            List<Long> result = new ArrayList<>();
            for (JsonNode item : node) {
                if (item != null && item.isNumber()) {
                    result.add(item.longValue());
                }
            }
            return result;
        } catch (Exception e) {
            return List.of();
        }
    }

    private String mergeTochkaIntoMetadata(
            String existing,
            String operationId,
            String paymentLink,
            String tochkaStatus
    ) {
        try {
            ObjectNode node = existing != null && !existing.isBlank()
                    ? (ObjectNode) objectMapper.readTree(existing)
                    : objectMapper.createObjectNode();
            node.put("operationId", operationId);
            node.put("paymentLink", paymentLink);
            if (tochkaStatus != null) {
                node.put("tochkaStatus", tochkaStatus);
            }
            return objectMapper.writeValueAsString(node);
        } catch (Exception e) {
            log.warn("Failed to merge payment metadata", e);
            return existing;
        }
    }

    private Long readLongMeta(String metadata, String field) {
        try {
            if (metadata == null || metadata.isBlank()) {
                return null;
            }
            JsonNode node = objectMapper.readTree(metadata).get(field);
            return node != null && node.isNumber() ? node.longValue() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private LocalDateTime readDateTimeMeta(String metadata, String field) {
        try {
            if (metadata == null || metadata.isBlank()) {
                return null;
            }
            JsonNode node = objectMapper.readTree(metadata).get(field);
            if (node == null || node.isNull()) {
                return null;
            }
            return LocalDateTime.parse(node.asText());
        } catch (Exception e) {
            return null;
        }
    }
}
