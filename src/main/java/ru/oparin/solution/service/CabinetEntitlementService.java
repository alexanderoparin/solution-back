package ru.oparin.solution.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.oparin.solution.model.*;
import ru.oparin.solution.repository.CabinetAccessGrantRepository;
import ru.oparin.solution.repository.SubscriptionRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Entitlement кабинета: PRO / agency → полный доступ; иначе услуги по подпискам кабинета.
 * Промокод FULL_ACCESS у текущего пользователя действует на кабинеты, к которым у него есть доступ.
 */
@Service
@RequiredArgsConstructor
public class CabinetEntitlementService {

    private static final List<String> ACTIVE_STATUSES = List.of("active", "trial");

    private final SubscriptionRepository subscriptionRepository;
    private final PromoCodeService promoCodeService;
    private final CabinetAccessGrantRepository grantRepository;

    /**
     * Полный доступ ко всем разделам без ограничений (как клиент агентства).
     * Учитывает только владельца кабинета — для фоновых задач и биллинга владельца.
     */
    @Transactional(readOnly = true)
    public boolean hasUnlimitedAccess(Cabinet cabinet) {
        if (cabinet == null) {
            return false;
        }
        User owner = cabinet.getUser();
        if (owner != null && Boolean.TRUE.equals(owner.getAgencyManaged())) {
            return true;
        }
        if (owner != null && promoCodeService.hasActiveFullAccess(owner.getId())) {
            return true;
        }
        return findActiveMainSubscription(cabinet)
                .map(s -> s.getPlan() != null && PlanCodes.PRO_MONTH.equals(s.getPlan().getCode()))
                .orElse(false);
    }

    /**
     * Полный доступ с учётом промокода текущего пользователя (собственный или выданный кабинет).
     */
    @Transactional(readOnly = true)
    public boolean hasUnlimitedAccess(Cabinet cabinet, User actor) {
        if (hasUnlimitedAccess(cabinet)) {
            return true;
        }
        return hasActorPromoAccess(cabinet, actor);
    }

    /**
     * Доступ к «Управление РК»: безлимит или активная услуга campaign_*.
     */
    @Transactional(readOnly = true)
    public boolean hasCampaignManageAccess(Cabinet cabinet) {
        if (hasUnlimitedAccess(cabinet)) {
            return true;
        }
        return findActiveCampaignSubscription(cabinet).isPresent();
    }

    /**
     * Доступ к «Управление РК» с учётом промокода текущего пользователя.
     */
    @Transactional(readOnly = true)
    public boolean hasCampaignManageAccess(Cabinet cabinet, User actor) {
        if (hasUnlimitedAccess(cabinet, actor)) {
            return true;
        }
        return findActiveCampaignSubscription(cabinet).isPresent();
    }

    @Transactional(readOnly = true)
    public Optional<Subscription> findActiveMainSubscription(Cabinet cabinet) {
        if (cabinet == null || cabinet.getId() == null) {
            return Optional.empty();
        }
        return subscriptionRepository.findFirstActiveByCabinetIdAndKind(
                cabinet.getId(), PlanKind.MAIN, ACTIVE_STATUSES, LocalDateTime.now());
    }

    @Transactional(readOnly = true)
    public Optional<Subscription> findActiveCampaignSubscription(Cabinet cabinet) {
        if (cabinet == null || cabinet.getId() == null) {
            return Optional.empty();
        }
        return subscriptionRepository.findFirstActiveByCabinetIdAndCodePrefix(
                cabinet.getId(), PlanCodes.CAMPAIGN_PLAN_PREFIX, ACTIVE_STATUSES, LocalDateTime.now());
    }

    @Transactional(readOnly = true)
    public Optional<Subscription> findLastExpiredCampaign(Cabinet cabinet) {
        if (cabinet == null || cabinet.getId() == null) {
            return Optional.empty();
        }
        return subscriptionRepository.findLastExpiredByCabinetIdAndCodePrefix(
                cabinet.getId(), PlanCodes.CAMPAIGN_PLAN_PREFIX, LocalDateTime.now());
    }

    /**
     * Активная активация промокода FULL_ACCESS у владельца кабинета.
     */
    @Transactional(readOnly = true)
    public Optional<PromoCodeRedemption> findActivePromoForCabinet(Cabinet cabinet) {
        if (cabinet == null || cabinet.getUser() == null || cabinet.getUser().getId() == null) {
            return Optional.empty();
        }
        return promoCodeService.findActiveFullAccessRedemption(cabinet.getUser().getId());
    }

    /**
     * Промокод для отображения billing/access: владелец кабинета или текущий пользователь с доступом.
     */
    @Transactional(readOnly = true)
    public Optional<PromoCodeRedemption> findActivePromoForAccess(Cabinet cabinet, User actor) {
        Optional<PromoCodeRedemption> ownerPromo = findActivePromoForCabinet(cabinet);
        if (ownerPromo.isPresent()) {
            return ownerPromo;
        }
        if (hasActorPromoAccess(cabinet, actor)) {
            return promoCodeService.findActiveFullAccessRedemption(actor.getId());
        }
        return Optional.empty();
    }

    private boolean hasActorPromoAccess(Cabinet cabinet, User actor) {
        if (cabinet == null || actor == null || actor.getId() == null) {
            return false;
        }
        if (!promoCodeService.hasActiveFullAccess(actor.getId())) {
            return false;
        }
        return actorHasCabinetAccess(actor, cabinet);
    }

    private boolean actorHasCabinetAccess(User actor, Cabinet cabinet) {
        if (actor.getRole() == Role.ADMIN) {
            return true;
        }
        User owner = cabinet.getUser();
        if (owner != null && owner.getId().equals(actor.getId())) {
            return true;
        }
        LocalDateTime now = LocalDateTime.now();
        return grantRepository.findByCabinet_IdAndUser_Id(cabinet.getId(), actor.getId())
                .filter(g -> g.getStatus() == CabinetAccessGrantStatus.ACTIVE)
                .filter(g -> g.getValidUntil() == null || g.getValidUntil().isAfter(now))
                .isPresent();
    }
}
