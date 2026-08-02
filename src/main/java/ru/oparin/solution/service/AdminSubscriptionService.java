package ru.oparin.solution.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.oparin.solution.dto.CabinetBillingOverviewDto;
import ru.oparin.solution.dto.CabinetBillingOverviewDto.AbTestsOverviewDto;
import ru.oparin.solution.dto.CabinetBillingOverviewDto.CampaignOverviewDto;
import ru.oparin.solution.dto.CabinetBillingOverviewDto.MainTariffOverviewDto;
import ru.oparin.solution.dto.PageResponse;
import ru.oparin.solution.dto.PaymentDto;
import ru.oparin.solution.dto.SubscriptionDto;
import ru.oparin.solution.exception.UserException;
import ru.oparin.solution.model.*;
import ru.oparin.solution.repository.*;
import ru.oparin.solution.repository.spec.CabinetManagedSpecifications;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Админ-действия: ручное продление подписки кабинета / начисление А/Б / обзор биллинга.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AdminSubscriptionService {

    private static final List<String> ACTIVE_STATUSES = List.of("active", "trial");

    private final UserRepository userRepository;
    private final PlanRepository planRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final PaymentRepository paymentRepository;
    private final CabinetRepository cabinetRepository;
    private final CabinetAbTestQuotaRepository quotaRepository;
    private final SubscriptionPaymentService subscriptionPaymentService;
    private final AbTestQuotaService abTestQuotaService;

    /**
     * Постраничный обзор тарифов и услуг кабинетов для админки.
     */
    @Transactional(readOnly = true)
    public PageResponse<CabinetBillingOverviewDto> pageCabinetBilling(
            User admin,
            int page,
            int size,
            String search
    ) {
        if (admin == null || admin.getRole() != Role.ADMIN) {
            throw new UserException("Доступ только для ADMIN", HttpStatus.FORBIDDEN);
        }
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);
        PageRequest pageable = PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.ASC, "name"));
        Page<Cabinet> cabinetPage = cabinetRepository.findAll(
                CabinetManagedSpecifications.managedList(admin, search, false),
                pageable
        );
        List<Cabinet> cabinets = cabinetPage.getContent();
        if (cabinets.isEmpty()) {
            return PageResponse.<CabinetBillingOverviewDto>builder()
                    .content(List.of())
                    .totalElements(cabinetPage.getTotalElements())
                    .totalPages(cabinetPage.getTotalPages())
                    .size(cabinetPage.getSize())
                    .number(cabinetPage.getNumber())
                    .build();
        }

        List<Long> ids = cabinets.stream().map(Cabinet::getId).toList();
        LocalDateTime now = LocalDateTime.now();
        List<Subscription> activeSubs = subscriptionRepository.findActiveByCabinetIdIn(ids, ACTIVE_STATUSES, now);
        Map<Long, Subscription> mainByCabinet = new HashMap<>();
        Map<Long, Subscription> campaignByCabinet = new HashMap<>();
        for (Subscription sub : activeSubs) {
            if (sub.getCabinet() == null || sub.getPlan() == null || sub.getPlan().getKind() == null) {
                continue;
            }
            Long cabinetId = sub.getCabinet().getId();
            if (sub.getPlan().getKind() == PlanKind.MAIN) {
                mainByCabinet.putIfAbsent(cabinetId, sub);
            } else if (sub.getPlan().getKind() == PlanKind.CAMPAIGN) {
                campaignByCabinet.putIfAbsent(cabinetId, sub);
            }
        }

        Map<Long, CabinetAbTestQuota> quotaByCabinet = quotaRepository.findByCabinetIdIn(ids).stream()
                .collect(Collectors.toMap(CabinetAbTestQuota::getCabinetId, q -> q, (a, b) -> a));

        List<CabinetBillingOverviewDto> rows = new ArrayList<>(cabinets.size());
        for (Cabinet cabinet : cabinets) {
            rows.add(toOverviewDto(cabinet, mainByCabinet.get(cabinet.getId()),
                    campaignByCabinet.get(cabinet.getId()), quotaByCabinet.get(cabinet.getId())));
        }

        return PageResponse.<CabinetBillingOverviewDto>builder()
                .content(rows)
                .totalElements(cabinetPage.getTotalElements())
                .totalPages(cabinetPage.getTotalPages())
                .size(cabinetPage.getSize())
                .number(cabinetPage.getNumber())
                .build();
    }

    private CabinetBillingOverviewDto toOverviewDto(
            Cabinet cabinet,
            Subscription main,
            Subscription campaign,
            CabinetAbTestQuota quota
    ) {
        User owner = cabinet.getUser();
        boolean agency = owner != null && Boolean.TRUE.equals(owner.getAgencyManaged());
        boolean unlimited = agency
                || (main != null && main.getPlan() != null
                && PlanCodes.PRO_MONTH.equals(main.getPlan().getCode()));

        MainTariffOverviewDto mainTariff;
        if (unlimited && (main == null || main.getPlan() == null
                || !PlanCodes.PRO_MONTH.equals(main.getPlan().getCode()))) {
            mainTariff = MainTariffOverviewDto.builder()
                    .code(PlanCodes.PRO_MONTH)
                    .name("PRO (клиент агентства)")
                    .status("AGENCY")
                    .expiresAt(null)
                    .unlimitedAccess(true)
                    .build();
        } else if (main != null && main.getPlan() != null) {
            mainTariff = MainTariffOverviewDto.builder()
                    .code(main.getPlan().getCode())
                    .name(main.getPlan().getName())
                    .status(main.getStatus())
                    .expiresAt(main.getExpiresAt())
                    .unlimitedAccess(unlimited)
                    .build();
        } else {
            mainTariff = MainTariffOverviewDto.builder()
                    .code(PlanCodes.ANALYTICS_FREE)
                    .name("Бесплатный доступ")
                    .status("active")
                    .expiresAt(null)
                    .unlimitedAccess(false)
                    .build();
        }

        CampaignOverviewDto campaignDto;
        if (unlimited) {
            campaignDto = CampaignOverviewDto.builder()
                    .connected(true)
                    .planCode(PlanCodes.PRO_MONTH)
                    .planName("Входит в PRO")
                    .expiresAt(null)
                    .status("INCLUDED")
                    .build();
        } else if (campaign != null && campaign.getPlan() != null) {
            campaignDto = CampaignOverviewDto.builder()
                    .connected(true)
                    .planCode(campaign.getPlan().getCode())
                    .planName(campaign.getPlan().getName())
                    .expiresAt(campaign.getExpiresAt())
                    .status("ACTIVE")
                    .build();
        } else {
            campaignDto = CampaignOverviewDto.builder()
                    .connected(false)
                    .status("NONE")
                    .build();
        }

        boolean activated = quota != null && Boolean.TRUE.equals(quota.getActivated());
        Integer remaining = quota != null ? quota.getRemaining() : null;
        AbTestsOverviewDto abDto = AbTestsOverviewDto.builder()
                .connected(unlimited || activated)
                .activated(activated)
                .remaining(unlimited ? null : remaining)
                .unlimited(unlimited)
                .build();

        return CabinetBillingOverviewDto.builder()
                .cabinetId(cabinet.getId())
                .cabinetName(cabinet.getName())
                .sellerId(owner != null ? owner.getId() : null)
                .sellerEmail(owner != null ? owner.getEmail() : null)
                .agencyManaged(agency)
                .mainTariff(mainTariff)
                .campaign(campaignDto)
                .abTests(abDto)
                .build();
    }

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
            log.info("Админ extend: request.userId={} cabinet.ownerId={}", explicit.getId(), owner.getId());
        }

        if (plan.getKind() == PlanKind.AB_PACK) {
            if (PlanCodes.AB_PACK_FREE.equals(plan.getCode()) && abCredits == null) {
                abTestQuotaService.activateFreeQuota(cabinet);
                log.info("Админ активировал бесплатный пакет А/Б cabinetId={}", cabinetId);
            } else {
                int credits = abCredits != null && abCredits > 0
                        ? abCredits
                        : (plan.getCreditAmount() != null ? plan.getCreditAmount() : 0);
                abTestQuotaService.addCredits(cabinet, credits);
                log.info("Админ начислил {} А/Б кредитов cabinetId={}", credits, cabinetId);
            }
            Subscription saved = subscriptionPaymentService.createOrExtendKindSubscription(owner, cabinet, plan);
            return toSubscriptionDto(saved);
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime targetExpiresAt = expiresAt != null
                ? expiresAt
                : SubscriptionPeriodUtils.addPlanPeriod(now, plan);

        Subscription current = subscriptionRepository
                .findFirstActiveByCabinetIdAndKind(
                        cabinet.getId(),
                        plan.getKind() != null ? plan.getKind() : PlanKind.CAMPAIGN,
                        ACTIVE_STATUSES,
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
