package ru.oparin.solution.service.campaign;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import ru.oparin.solution.config.SubscriptionProperties;
import ru.oparin.solution.dto.CampaignManageAccessDto;
import ru.oparin.solution.exception.UserException;
import ru.oparin.solution.model.PlanProductCode;
import ru.oparin.solution.model.Role;
import ru.oparin.solution.model.Subscription;
import ru.oparin.solution.model.User;
import ru.oparin.solution.repository.SubscriptionRepository;
import ru.oparin.solution.service.SellerWorkerService;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Проверка доступа к разделу «Управление РК» по подписке селлера.
 */
@Service
@RequiredArgsConstructor
public class CampaignManageAccessService {

    public static final String SUBSCRIPTION_REQUIRED_CODE = "CAMPAIGN_MANAGE_SUBSCRIPTION_REQUIRED";

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

    public boolean hasAccess(User actor, User seller) {
        if (!isCampaignManagementEnabled()) {
            return true;
        }
        if (actor != null && actor.getRole() == Role.ADMIN) {
            return true;
        }
        User holder = resolveSubscriptionHolder(actor, seller);
        if (holder == null) {
            return false;
        }
        return findActiveSubscription(holder).isPresent();
    }

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
        if (actor != null && actor.getRole() == Role.ADMIN) {
            return CampaignManageAccessDto.builder()
                    .enabled(true)
                    .hasAccess(true)
                    .status("ACTIVE")
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

        LocalDateTime now = LocalDateTime.now();
        boolean canActivateFree = !subscriptionRepository.existsByUser_IdAndPlan_Code(
                holder.getId(), PlanProductCode.CAMPAIGN_FREE);

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
                            .findFirstByUser_IdAndPlan_ProductCodeAndExpiresAtBeforeOrderByExpiresAtDesc(
                                    holder.getId(), PlanProductCode.CAMPAIGN_MANAGE, now)
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

    public boolean hasAccessForCabinetOwner(Long cabinetUserId) {
        if (!isCampaignManagementEnabled()) {
            return true;
        }
        // cabinet owner is seller - loaded by id elsewhere
        return subscriptionRepository
                .findFirstByUser_IdAndPlan_ProductCodeAndStatusInAndExpiresAtAfterOrderByExpiresAtDesc(
                        cabinetUserId,
                        PlanProductCode.CAMPAIGN_MANAGE,
                        ACTIVE_STATUSES,
                        LocalDateTime.now()
                )
                .isPresent();
    }

    private java.util.Optional<Subscription> findActiveSubscription(User holder) {
        return subscriptionRepository.findFirstByUser_IdAndPlan_ProductCodeAndStatusInAndExpiresAtAfterOrderByExpiresAtDesc(
                holder.getId(),
                PlanProductCode.CAMPAIGN_MANAGE,
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
