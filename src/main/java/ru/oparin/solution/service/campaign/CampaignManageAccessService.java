package ru.oparin.solution.service.campaign;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import ru.oparin.solution.config.SubscriptionProperties;
import ru.oparin.solution.dto.CampaignManageAccessDto;
import ru.oparin.solution.exception.UserException;
import ru.oparin.solution.model.PlanCodes;
import ru.oparin.solution.model.Role;
import ru.oparin.solution.model.Subscription;
import ru.oparin.solution.model.User;
import ru.oparin.solution.repository.SubscriptionRepository;
import ru.oparin.solution.service.SellerWorkerService;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

/**
 * Проверка entitlement на «Управление РК»: подписка campaign_* или клиент агентства ({@code agency_managed}).
 * <p>
 * Используется и для ручных операций (UI/API), и для планировщика расписания.
 */
@Service
@RequiredArgsConstructor
public class CampaignManageAccessService {

    public static final String SUBSCRIPTION_REQUIRED_CODE = "CAMPAIGN_MANAGE_SUBSCRIPTION_REQUIRED";
    public static final String STATUS_AGENCY = "AGENCY";

    public static final String SCHEDULE_STOPPED_SUBSCRIPTION_EXPIRED =
            "Расписание отключено: истекла подписка на «Управление РК». "
                    + "Продлите подписку и нажмите «Запустить», чтобы снова включить автоматический запуск.";
    public static final String SCHEDULE_STOPPED_NO_SUBSCRIPTION =
            "Расписание отключено: нет активной подписки на «Управление РК». "
                    + "Оформите подписку и нажмите «Запустить» для автоматического запуска.";

    private static final List<String> ACTIVE_STATUSES = List.of("active", "trial");

    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionProperties subscriptionProperties;
    private final SellerWorkerService sellerWorkerService;

    /**
     * Пользователь, на чью подписку смотрим (селлер).
     */
    public User resolveSubscriptionHolder(User actor, User seller) {
        if (seller != null) {
            return seller;
        }
        if (actor == null) {
            return null;
        }
        if (actor.getRole() == Role.SELLER) {
            return actor;
        }
        if (actor.getRole() == Role.WORKER) {
            return sellerWorkerService.findSellerByWorkerId(actor.getId()).orElse(null);
        }
        return null;
    }

    public boolean isCampaignManagementEnabled() {
        return subscriptionProperties.isCampaignManagementEnabled();
    }

    /**
     * Право на автоматику и управление РК для владельца кабинета (селлера).
     */
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
        return findActiveSubscription(seller).isPresent();
    }

    /**
     * Текст события в журнале РК при отключении расписания из-за отсутствия entitlement.
     */
    public String scheduleStopMessageForSeller(User seller) {
        if (seller == null) {
            return SCHEDULE_STOPPED_NO_SUBSCRIPTION;
        }
        LocalDateTime now = LocalDateTime.now();
        boolean hadCampaignSubscription = subscriptionRepository
                .findFirstByUser_IdAndPlan_CodeStartingWithAndExpiresAtBeforeOrderByExpiresAtDesc(
                        seller.getId(), "campaign_", now)
                .isPresent();
        return hadCampaignSubscription ? SCHEDULE_STOPPED_SUBSCRIPTION_EXPIRED : SCHEDULE_STOPPED_NO_SUBSCRIPTION;
    }

    /**
     * Есть ли право на ручные операции управления РК для текущего контекста.
     */
    public boolean hasAccess(User actor, User seller) {
        if (!isCampaignManagementEnabled()) {
            return true;
        }
        User holder = resolveSubscriptionHolder(actor, seller);
        return hasCampaignEntitlement(holder);
    }

    /**
     * Требует entitlement для ручных операций; при отсутствии — {@link UserException} 403.
     */
    public void requireAccess(User actor, User seller) {
        if (!hasAccess(actor, seller)) {
            throw new UserException(
                    "Для использования Управления РК необходима подписка",
                    HttpStatus.FORBIDDEN
            );
        }
    }

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
        if (holder == null) {
            return CampaignManageAccessDto.builder()
                    .enabled(true)
                    .hasAccess(false)
                    .status("NONE")
                    .canActivateFree(false)
                    .build();
        }

        if (Boolean.TRUE.equals(holder.getAgencyManaged())) {
            return CampaignManageAccessDto.builder()
                    .enabled(true)
                    .hasAccess(true)
                    .status(STATUS_AGENCY)
                    .canActivateFree(false)
                    .build();
        }

        LocalDateTime now = LocalDateTime.now();
        boolean canActivateFree = !subscriptionRepository.existsByUser_IdAndPlan_Code(
                holder.getId(), PlanCodes.CAMPAIGN_FREE);

        return findActiveSubscription(holder)
                .map(sub -> CampaignManageAccessDto.builder()
                        .enabled(true)
                        .hasAccess(true)
                        .status("ACTIVE")
                        .expiresAt(sub.getExpiresAt())
                        .daysRemaining(daysBetweenCeil(now, sub.getExpiresAt()))
                        .canActivateFree(canActivateFree)
                        .build())
                .orElseGet(() -> {
                    Subscription expired = subscriptionRepository
                            .findFirstByUser_IdAndExpiresAtBeforeOrderByExpiresAtDesc(
                                    holder.getId(), now)
                            .orElse(null);
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

    private Optional<Subscription> findActiveSubscription(User holder) {
        return subscriptionRepository.findFirstByUser_IdAndStatusInAndExpiresAtAfterOrderByExpiresAtDesc(
                holder.getId(),
                ACTIVE_STATUSES,
                LocalDateTime.now()
        );
    }

    private static int daysBetweenCeil(LocalDateTime from, LocalDateTime to) {
        if (to.isBefore(from) || to.isEqual(from)) {
            return 0;
        }
        return (int) ChronoUnit.DAYS.between(from.toLocalDate(), to.toLocalDate());
    }
}
