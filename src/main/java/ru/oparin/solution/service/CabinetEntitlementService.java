package ru.oparin.solution.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.oparin.solution.model.*;
import ru.oparin.solution.repository.SubscriptionRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Entitlement кабинета: PRO / agency → полный доступ; иначе услуги по подпискам кабинета.
 */
@Service
@RequiredArgsConstructor
public class CabinetEntitlementService {

    private static final List<String> ACTIVE_STATUSES = List.of("active", "trial");

    private final SubscriptionRepository subscriptionRepository;
    private final PromoCodeService promoCodeService;

    /**
     * Полный доступ ко всем разделам без ограничений (как клиент агентства).
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
     * Доступ к «Управление РК»: безлимит или активная услуга campaign_*.
     */
    @Transactional(readOnly = true)
    public boolean hasCampaignManageAccess(Cabinet cabinet) {
        if (hasUnlimitedAccess(cabinet)) {
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
}
