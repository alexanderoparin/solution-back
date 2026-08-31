package ru.oparin.solution.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.oparin.solution.dto.CabinetBillingStatusDto;
import ru.oparin.solution.dto.CabinetBillingStatusDto.MainTariffDto;
import ru.oparin.solution.dto.CabinetBillingStatusDto.ServiceStatusDto;
import ru.oparin.solution.dto.WbAbTestQuotaDto;
import ru.oparin.solution.exception.UserException;
import ru.oparin.solution.model.*;
import ru.oparin.solution.repository.CabinetRepository;
import ru.oparin.solution.repository.PlanRepository;
import ru.oparin.solution.repository.SubscriptionRepository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Статус тарифов/услуг кабинета и инициализация FREE при создании кабинета.
 */
@Service
@RequiredArgsConstructor
public class CabinetBillingService {

    public static final String SERVICE_CAMPAIGN_MANAGE = "CAMPAIGN_MANAGE";
    public static final String SERVICE_AB_TESTS = "AB_TESTS";

    private static final List<String> ACTIVE_STATUSES = List.of("active", "trial");

    private final CabinetRepository cabinetRepository;
    private final PlanRepository planRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final CabinetEntitlementService entitlementService;
    private final WbAbTestQuotaService abTestQuotaService;
    private final SubscriptionPaymentService subscriptionPaymentService;

    /**
     * FREE-подписка + квота А/Б для нового кабинета.
     */
    @Transactional
    public void initializeCabinetBilling(Cabinet cabinet) {
        Plan freePlan = planRepository.findByCode(PlanCodes.ANALYTICS_FREE)
                .orElseThrow(() -> new IllegalStateException("План analytics_free не найден"));
        LocalDateTime now = LocalDateTime.now();
        boolean hasMain = subscriptionRepository
                .findFirstActiveByCabinetIdAndKind(cabinet.getId(), PlanKind.MAIN, ACTIVE_STATUSES, now)
                .isPresent();
        if (!hasMain) {
            subscriptionRepository.save(Subscription.builder()
                    .user(cabinet.getUser())
                    .cabinet(cabinet)
                    .plan(freePlan)
                    .status("active")
                    .startedAt(now)
                    .expiresAt(null)
                    .autoRenew(true)
                    .build());
        }
        abTestQuotaService.ensureQuota(cabinet);
    }

    @Transactional(readOnly = true)
    public CabinetBillingStatusDto buildStatus(User actor, Long cabinetId) {
        Cabinet cabinet = cabinetRepository.findByIdWithUser(cabinetId)
                .orElseThrow(() -> new UserException("Кабинет не найден", HttpStatus.NOT_FOUND));
        boolean canManage = actor != null
                && cabinet.getUser() != null
                && (actor.getRole() == Role.ADMIN || cabinet.getUser().getId().equals(actor.getId()));

        boolean unlimited = entitlementService.hasUnlimitedAccess(cabinet);
        Subscription main = entitlementService.findActiveMainSubscription(cabinet).orElse(null);
        MainTariffDto mainTariff = buildMainTariff(cabinet, unlimited, main);
        LocalDateTime promoExpiresAt = entitlementService.findActivePromoForCabinet(cabinet)
                .map(PromoCodeRedemption::getExpiresAt)
                .orElse(null);

        List<ServiceStatusDto> services = new ArrayList<>();
        services.add(buildCampaignService(cabinet, unlimited, promoExpiresAt));
        services.add(buildAbService(cabinet, unlimited));

        return CabinetBillingStatusDto.builder()
                .cabinetId(cabinetId)
                .mainTariff(mainTariff)
                .services(services)
                .abTestQuota(abTestQuotaService.getQuotaDto(cabinet))
                .canManageBilling(canManage)
                .build();
    }

    private MainTariffDto buildMainTariff(Cabinet cabinet, boolean unlimited, Subscription main) {
        if (!unlimited) {
            if (main != null && main.getPlan() != null) {
                return MainTariffDto.builder()
                        .code(main.getPlan().getCode())
                        .name(main.getPlan().getName())
                        .status(main.getStatus())
                        .expiresAt(main.getExpiresAt())
                        .unlimitedAccess(false)
                        .build();
            }
            return MainTariffDto.builder()
                    .code(PlanCodes.ANALYTICS_FREE)
                    .name("Бесплатный доступ")
                    .status("active")
                    .expiresAt(null)
                    .unlimitedAccess(false)
                    .build();
        }

        User owner = cabinet.getUser();
        boolean agency = owner != null && Boolean.TRUE.equals(owner.getAgencyManaged());
        var promo = entitlementService.findActivePromoForCabinet(cabinet);
        if (promo.isPresent() && !agency) {
            PromoCode promoCode = promo.get().getPromoCode();
            return MainTariffDto.builder()
                    .code(PlanCodes.PRO_MONTH)
                    .name("PRO (промокод " + promoCode.getCode() + ")")
                    .status("PROMO")
                    .expiresAt(promo.get().getExpiresAt())
                    .unlimitedAccess(true)
                    .build();
        }

        if (main != null && main.getPlan() != null && PlanCodes.PRO_MONTH.equals(main.getPlan().getCode())) {
            return MainTariffDto.builder()
                    .code(main.getPlan().getCode())
                    .name(main.getPlan().getName())
                    .status(main.getStatus())
                    .expiresAt(main.getExpiresAt())
                    .unlimitedAccess(true)
                    .build();
        }

        return MainTariffDto.builder()
                .code(PlanCodes.PRO_MONTH)
                .name("PRO (клиент агентства)")
                .status("AGENCY")
                .expiresAt(null)
                .unlimitedAccess(true)
                .build();
    }

    private ServiceStatusDto buildCampaignService(Cabinet cabinet, boolean unlimited, LocalDateTime promoExpiresAt) {
        if (unlimited) {
            return ServiceStatusDto.builder()
                    .serviceCode(SERVICE_CAMPAIGN_MANAGE)
                    .name("Управление РК")
                    .connected(true)
                    .planCode(PlanCodes.PRO_MONTH)
                    .planName(promoExpiresAt != null ? "Промокод" : "Входит в PRO")
                    .expiresAt(promoExpiresAt)
                    .status("INCLUDED")
                    .build();
        }
        return entitlementService.findActiveCampaignSubscription(cabinet)
                .map(sub -> ServiceStatusDto.builder()
                        .serviceCode(SERVICE_CAMPAIGN_MANAGE)
                        .name("Управление РК")
                        .connected(true)
                        .planCode(sub.getPlan() != null ? sub.getPlan().getCode() : null)
                        .planName(sub.getPlan() != null ? sub.getPlan().getName() : null)
                        .expiresAt(sub.getExpiresAt())
                        .status("ACTIVE")
                        .build())
                .orElseGet(() -> {
                    Subscription expired = entitlementService.findLastExpiredCampaign(cabinet).orElse(null);
                    return ServiceStatusDto.builder()
                            .serviceCode(SERVICE_CAMPAIGN_MANAGE)
                            .name("Управление РК")
                            .connected(false)
                            .expiresAt(expired != null ? expired.getExpiresAt() : null)
                            .status(expired != null ? "EXPIRED" : "NONE")
                            .build();
                });
    }

    private ServiceStatusDto buildAbService(Cabinet cabinet, boolean unlimited) {
        var quota = abTestQuotaService.getQuotaDto(cabinet);
        boolean connected = unlimited || Boolean.TRUE.equals(quota.getActivated());
        String planName;
        if (unlimited) {
            planName = "Безлимит (PRO)";
        } else if (connected && quota.getRemaining() != null) {
            planName = "Доступно тестов: " + quota.getRemaining();
        } else {
            planName = null;
        }
        return ServiceStatusDto.builder()
                .serviceCode(SERVICE_AB_TESTS)
                .name("А/Б тесты")
                .connected(connected)
                .planName(planName)
                .status(unlimited ? "INCLUDED" : (connected ? "ACTIVE" : "NONE"))
                .build();
    }

    /**
     * Явно активирует бесплатный пакет А/Б тестов кабинета (владелец).
     * Количество кредитов — из плана ab_pack_free (правится в админке).
     * Также создаёт/обновляет запись услуги в {@code subscriptions} (kind AB_PACK).
     */
    @Transactional
    public WbAbTestQuotaDto activateAbFreeQuota(User actor, Long cabinetId) {
        Cabinet cabinet = cabinetRepository.findById(cabinetId)
                .orElseThrow(() -> new UserException("Кабинет не найден", HttpStatus.NOT_FOUND));
        boolean canManage = actor != null
                && cabinet.getUser() != null
                && (actor.getRole() == Role.ADMIN || cabinet.getUser().getId().equals(actor.getId()));
        if (!canManage) {
            throw new UserException("Только владелец кабинета может подключить услугу", HttpStatus.FORBIDDEN);
        }
        WbAbTestQuotaDto quota = abTestQuotaService.activateFreeQuota(cabinet);
        Plan freePack = planRepository.findByCode(PlanCodes.AB_PACK_FREE)
                .orElseThrow(() -> new UserException(
                        "План ab_pack_free не найден — примените миграцию 084",
                        HttpStatus.INTERNAL_SERVER_ERROR));
        subscriptionPaymentService.createOrExtendKindSubscription(cabinet.getUser(), cabinet, freePack);
        return quota;
    }
}
