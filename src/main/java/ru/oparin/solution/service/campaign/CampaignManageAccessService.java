package ru.oparin.solution.service.campaign;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import ru.oparin.solution.config.SubscriptionProperties;
import ru.oparin.solution.dto.CampaignManageAccessDto;
import ru.oparin.solution.exception.UserException;
import ru.oparin.solution.model.*;
import ru.oparin.solution.repository.SubscriptionRepository;
import ru.oparin.solution.service.CabinetEntitlementService;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * Проверка entitlement на «Управление РК» на уровне кабинета:
 * PRO / agency_managed / активная услуга campaign_*.
 */
@Service
@RequiredArgsConstructor
public class CampaignManageAccessService {

    public static final String SUBSCRIPTION_REQUIRED_CODE = "CAMPAIGN_MANAGE_SUBSCRIPTION_REQUIRED";
    public static final String STATUS_AGENCY = "AGENCY";
    public static final String STATUS_PRO = "PRO";

    public static final String SCHEDULE_STOPPED_SUBSCRIPTION_EXPIRED =
            "Расписание отключено: истекла подписка на «Управление РК». "
                    + "Продлите подписку и нажмите «Запустить», чтобы снова включить автоматический запуск.";
    public static final String SCHEDULE_STOPPED_NO_SUBSCRIPTION =
            "Расписание отключено: нет активной подписки на «Управление РК». "
                    + "Оформите подписку и нажмите «Запустить» для автоматического запуска.";

    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionProperties subscriptionProperties;
    private final CabinetEntitlementService cabinetEntitlementService;

    /**
     * Пользователь-селлер (владелец), если нужен для UI без кабинета.
     */
    public User resolveSubscriptionHolder(User actor, User seller) {
        if (seller != null) {
            return seller;
        }
        if (actor == null) {
            return null;
        }
        if (actor.getRole() == Role.USER || actor.getRole() == Role.ADMIN) {
            return actor;
        }
        return null;
    }

    public boolean isCampaignManagementEnabled() {
        return subscriptionProperties.isCampaignManagementEnabled();
    }

    /**
     * Право на автоматику и управление РК для кабинета.
     */
    public boolean hasCampaignEntitlement(Cabinet cabinet) {
        if (!isCampaignManagementEnabled()) {
            return true;
        }
        return cabinetEntitlementService.hasCampaignManageAccess(cabinet);
    }

    /**
     * @deprecated предпочитайте {@link #hasCampaignEntitlement(Cabinet)}.
     */
    @Deprecated
    public boolean hasCampaignEntitlement(User seller) {
        if (!isCampaignManagementEnabled()) {
            return true;
        }
        if (seller == null) {
            return false;
        }
        if (Boolean.TRUE.equals(seller.getAgencyManaged())) {
            return true;
        }
        // Без кабинета — нет cabinet-scoped подписки
        return false;
    }

    public String scheduleStopMessageForCabinet(Cabinet cabinet) {
        if (cabinet == null) {
            return SCHEDULE_STOPPED_NO_SUBSCRIPTION;
        }
        boolean had = cabinetEntitlementService.findLastExpiredCampaign(cabinet).isPresent();
        return had ? SCHEDULE_STOPPED_SUBSCRIPTION_EXPIRED : SCHEDULE_STOPPED_NO_SUBSCRIPTION;
    }

    /**
     * @deprecated предпочитайте {@link #scheduleStopMessageForCabinet(Cabinet)}.
     */
    @Deprecated
    public String scheduleStopMessageForSeller(User seller) {
        return SCHEDULE_STOPPED_NO_SUBSCRIPTION;
    }

    public boolean hasAccess(User actor, Cabinet cabinet) {
        if (!isCampaignManagementEnabled()) {
            return true;
        }
        return hasCampaignEntitlement(cabinet);
    }

    /**
     * @deprecated предпочитайте {@link #hasAccess(User, Cabinet)}.
     */
    @Deprecated
    public boolean hasAccess(User actor, User seller) {
        if (!isCampaignManagementEnabled()) {
            return true;
        }
        User holder = resolveSubscriptionHolder(actor, seller);
        return hasCampaignEntitlement(holder);
    }

    public void requireAccess(User actor, Cabinet cabinet) {
        if (!hasAccess(actor, cabinet)) {
            throw new UserException(
                    "Для использования Управления РК необходима подписка",
                    HttpStatus.FORBIDDEN
            );
        }
    }

    /**
     * @deprecated предпочитайте {@link #requireAccess(User, Cabinet)}.
     */
    @Deprecated
    public void requireAccess(User actor, User seller) {
        if (!hasAccess(actor, seller)) {
            throw new UserException(
                    "Для использования Управления РК необходима подписка",
                    HttpStatus.FORBIDDEN
            );
        }
    }

    public CampaignManageAccessDto buildAccessState(User actor, Cabinet cabinet) {
        if (!isCampaignManagementEnabled()) {
            return CampaignManageAccessDto.builder()
                    .enabled(false)
                    .hasAccess(true)
                    .status("NONE")
                    .canActivateFree(false)
                    .build();
        }
        if (cabinet == null) {
            return CampaignManageAccessDto.builder()
                    .enabled(true)
                    .hasAccess(false)
                    .status("NONE")
                    .canActivateFree(false)
                    .build();
        }

        User owner = cabinet.getUser();
        if (owner != null && Boolean.TRUE.equals(owner.getAgencyManaged())) {
            return CampaignManageAccessDto.builder()
                    .enabled(true)
                    .hasAccess(true)
                    .status(STATUS_AGENCY)
                    .canActivateFree(false)
                    .build();
        }

        if (cabinetEntitlementService.hasUnlimitedAccess(cabinet)) {
            Subscription pro = cabinetEntitlementService.findActiveMainSubscription(cabinet).orElse(null);
            return CampaignManageAccessDto.builder()
                    .enabled(true)
                    .hasAccess(true)
                    .status(STATUS_PRO)
                    .expiresAt(pro != null ? pro.getExpiresAt() : null)
                    .daysRemaining(pro != null && pro.getExpiresAt() != null
                            ? daysBetweenCeil(LocalDateTime.now(), pro.getExpiresAt())
                            : null)
                    .canActivateFree(false)
                    .build();
        }

        LocalDateTime now = LocalDateTime.now();
        boolean canActivateFree = !subscriptionRepository.existsByCabinet_IdAndPlan_Code(
                cabinet.getId(), PlanCodes.CAMPAIGN_FREE);

        return cabinetEntitlementService.findActiveCampaignSubscription(cabinet)
                .map(sub -> CampaignManageAccessDto.builder()
                        .enabled(true)
                        .hasAccess(true)
                        .status("ACTIVE")
                        .expiresAt(sub.getExpiresAt())
                        .daysRemaining(daysBetweenCeil(now, sub.getExpiresAt()))
                        .canActivateFree(canActivateFree)
                        .build())
                .orElseGet(() -> {
                    Subscription expired = cabinetEntitlementService.findLastExpiredCampaign(cabinet).orElse(null);
                    if (expired != null) {
                        int daysAgo = daysBetweenCeil(expired.getExpiresAt(), now);
                        return CampaignManageAccessDto.builder()
                                .enabled(true)
                                .hasAccess(false)
                                .status("EXPIRED")
                                .expiresAt(expired.getExpiresAt())
                                .daysExpiredAgo(daysAgo)
                                .canActivateFree(canActivateFree)
                                .build();
                    }
                    return CampaignManageAccessDto.builder()
                            .enabled(true)
                            .hasAccess(false)
                            .status("NONE")
                            .canActivateFree(canActivateFree)
                            .build();
                });
    }

    /**
     * Состояние без кабинета (legacy /access) — только agency.
     */
    public CampaignManageAccessDto buildAccessState(User actor, User seller) {
        if (!isCampaignManagementEnabled()) {
            return CampaignManageAccessDto.builder()
                    .enabled(false)
                    .hasAccess(true)
                    .status("NONE")
                    .canActivateFree(false)
                    .build();
        }
        User holder = resolveSubscriptionHolder(actor, seller);
        if (holder != null && Boolean.TRUE.equals(holder.getAgencyManaged())) {
            return CampaignManageAccessDto.builder()
                    .enabled(true)
                    .hasAccess(true)
                    .status(STATUS_AGENCY)
                    .canActivateFree(false)
                    .build();
        }
        return CampaignManageAccessDto.builder()
                .enabled(true)
                .hasAccess(false)
                .status("NONE")
                .canActivateFree(false)
                .build();
    }

    private static int daysBetweenCeil(LocalDateTime from, LocalDateTime to) {
        if (to == null || from == null || to.isBefore(from) || to.isEqual(from)) {
            return 0;
        }
        return (int) ChronoUnit.DAYS.between(from.toLocalDate(), to.toLocalDate());
    }
}
