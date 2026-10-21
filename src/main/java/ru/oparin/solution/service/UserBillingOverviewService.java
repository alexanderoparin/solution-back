package ru.oparin.solution.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.oparin.solution.dto.UserBillingOverviewDto;
import ru.oparin.solution.dto.UserBillingOverviewDto.ActivePackageDto;
import ru.oparin.solution.dto.UserBillingOverviewDto.CabinetBillingRowDto;
import ru.oparin.solution.dto.WbAbTestQuotaDto;
import ru.oparin.solution.model.*;
import ru.oparin.solution.repository.CabinetRepository;
import ru.oparin.solution.repository.SubscriptionRepository;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Сводка подписок владельца для страницы тарифов.
 */
@Service
@RequiredArgsConstructor
public class UserBillingOverviewService {

    private static final List<String> ACTIVE_STATUSES = List.of("active", "trial");

    private final CabinetRepository cabinetRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final CabinetEntitlementService entitlementService;
    private final WbAbTestQuotaService abTestQuotaService;
    private final PackageManagementService packageManagementService;

    @Transactional(readOnly = true)
    public UserBillingOverviewDto buildOverview(User user) {
        List<Cabinet> cabinets = cabinetRepository.findByUser_IdOrderByCreatedAtDesc(user.getId());
        WbAbTestQuotaDto abQuota = abTestQuotaService.getUserQuotaDto(user.getId());
        boolean hasUserAb = abQuota.getRemaining() != null && abQuota.getRemaining() > 0;

        List<CabinetBillingRowDto> rows = new ArrayList<>();
        Map<Long, List<Subscription>> proByPayment = new HashMap<>();
        Map<Long, List<Subscription>> campaignByPayment = new HashMap<>();
        List<Subscription> orphanPro = new ArrayList<>();
        List<Subscription> orphanCampaign = new ArrayList<>();

        for (Cabinet cabinet : cabinets) {
            boolean unlimited = entitlementService.hasUnlimitedAccess(cabinet, user);
            Subscription main = entitlementService.findActiveMainSubscription(cabinet).orElse(null);
            Subscription campaign = entitlementService.findActiveCampaignSubscription(cabinet).orElse(null);
            boolean pro = unlimited || (main != null && main.getPlan() != null
                    && PlanCodes.isProCode(main.getPlan().getCode()));
            boolean campaignActive = unlimited || campaign != null;
            boolean abActive = pro || hasUserAb;

            LocalDateTime expiresAt = null;
            String tariffLabel = "Бесплатный доступ";
            String mainCode = main != null && main.getPlan() != null ? main.getPlan().getCode() : PlanCodes.ANALYTICS_FREE;
            String campaignCode = campaign != null && campaign.getPlan() != null ? campaign.getPlan().getCode() : null;

            if (pro && !Boolean.TRUE.equals(user.getAgencyManaged())
                    && entitlementService.findActivePromoForCabinet(cabinet).isEmpty()) {
                if (main != null && PlanCodes.isProCode(main.getPlan() != null ? main.getPlan().getCode() : null)) {
                    expiresAt = main.getExpiresAt();
                    tariffLabel = "Полный доступ";
                    if (main.getPayment() != null) {
                        proByPayment.computeIfAbsent(main.getPayment().getId(), k -> new ArrayList<>()).add(main);
                    } else {
                        orphanPro.add(main);
                    }
                } else {
                    tariffLabel = "Полный доступ";
                }
            } else if (Boolean.TRUE.equals(user.getAgencyManaged()) || unlimited) {
                tariffLabel = "Полный доступ";
                expiresAt = main != null ? main.getExpiresAt() : null;
            } else if (campaign != null) {
                expiresAt = campaign.getExpiresAt();
                tariffLabel = "Управление РК";
                if (campaign.getPayment() != null) {
                    campaignByPayment.computeIfAbsent(campaign.getPayment().getId(), k -> new ArrayList<>()).add(campaign);
                } else {
                    orphanCampaign.add(campaign);
                }
            }

            rows.add(CabinetBillingRowDto.builder()
                    .cabinetId(cabinet.getId())
                    .name(cabinet.getName())
                    .marketplaceType(cabinet.getMarketplaceType() != null
                            ? cabinet.getMarketplaceType().name() : null)
                    .createdAt(cabinet.getCreatedAt())
                    .tariffLabel(tariffLabel)
                    .status(pro || campaignActive ? "ACTIVE" : "FREE")
                    .expiresAt(expiresAt)
                    .products(true)
                    .summary(true)
                    .campaigns(true)
                    .hypotheses(true)
                    .campaignManage(campaignActive)
                    .abTests(abActive)
                    .unlimitedAccess(unlimited)
                    .mainPlanCode(mainCode)
                    .campaignPlanCode(campaignCode)
                    .build());
        }

        List<ActivePackageDto> packages = new ArrayList<>();
        packages.addAll(aggregatePackages(proByPayment, orphanPro, PlanKind.MAIN.name(), "Полный доступ"));
        packages.addAll(aggregatePackages(campaignByPayment, orphanCampaign, PlanKind.CAMPAIGN.name(), "Управление РК"));

        if (Boolean.TRUE.equals(abQuota.getActivated())
                && (abQuota.getRemaining() != null && abQuota.getRemaining() > 0
                || abQuota.getUsedStarts() != null && abQuota.getUsedStarts() > 0)) {
            int rem = abQuota.getRemaining() != null ? abQuota.getRemaining() : 0;
            int used = abQuota.getUsedStarts() != null ? abQuota.getUsedStarts() : 0;
            packages.add(ActivePackageDto.builder()
                    .kind(PlanKind.AB_PACK.name())
                    .title("A/B-тесты")
                    .cabinetCount(null)
                    .expiresAt(null)
                    .status("ACTIVE")
                    .remaining(rem)
                    .usedStarts(used)
                    .build());
        }

        boolean canManage = user.getRole() == Role.ADMIN
                || (user.getRole() == Role.USER && Boolean.TRUE.equals(user.getEmailConfirmed()));

        return UserBillingOverviewDto.builder()
                .activePackages(packages)
                .cabinets(rows)
                .abTestQuota(abQuota)
                .canManageBilling(canManage)
                .build();
    }

    private List<ActivePackageDto> aggregatePackages(
            Map<Long, List<Subscription>> byPayment,
            List<Subscription> orphans,
            String kind,
            String title
    ) {
        List<ActivePackageDto> result = new ArrayList<>();
        for (Map.Entry<Long, List<Subscription>> entry : byPayment.entrySet()) {
            List<Subscription> group = entry.getValue();
            if (group.isEmpty()) {
                continue;
            }
            result.add(toPackageDto(kind, title, group, entry.getKey()));
        }
        for (Subscription sub : orphans) {
            result.add(toPackageDto(kind, title, List.of(sub), null));
        }
        return result;
    }

    private ActivePackageDto toPackageDto(
            String kind,
            String title,
            List<Subscription> group,
            Long paymentId
    ) {
        LocalDateTime expires = group.stream()
                .map(Subscription::getExpiresAt)
                .filter(e -> e != null)
                .min(Comparator.naturalOrder())
                .orElse(null);
        Plan plan = group.get(0).getPlan();
        int max = plan != null && plan.getMaxCabinets() != null ? plan.getMaxCabinets() : 1;
        int min = plan != null && plan.getMinCabinets() != null ? plan.getMinCabinets() : 1;
        List<Long> cabinetIds = group.stream()
                .map(Subscription::getCabinet)
                .filter(Objects::nonNull)
                .map(Cabinet::getId)
                .toList();
        boolean canAdd = paymentId != null && cabinetIds.size() < max;
        boolean canUpgrade = paymentId != null
                && plan != null
                && !packageManagementService.listUpgradeTargets(plan).isEmpty();
        return ActivePackageDto.builder()
                .kind(kind)
                .title(title)
                .cabinetCount(cabinetIds.size())
                .expiresAt(expires)
                .status("ACTIVE")
                .paymentId(paymentId)
                .planId(plan != null ? plan.getId() : null)
                .planCode(plan != null ? plan.getCode() : null)
                .minCabinets(min)
                .maxCabinets(max)
                .cabinetIds(cabinetIds)
                .canAddCabinets(canAdd)
                .canUpgrade(canUpgrade)
                .build();
    }
}
