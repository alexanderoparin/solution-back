package ru.oparin.solution.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.oparin.solution.dto.ozon.OzonSellerInfoResponse;
import ru.oparin.solution.model.Cabinet;
import ru.oparin.solution.model.CabinetSyncState;
import ru.oparin.solution.model.MarketplaceType;
import ru.oparin.solution.model.OzonSellerSubscriptionType;
import ru.oparin.solution.repository.CabinetSyncStateRepository;
import ru.oparin.solution.service.ozon.OzonPremiumLkProbeResult;
import ru.oparin.solution.service.ozon.OzonPremiumLkProbeService;
import ru.oparin.solution.service.ozon.OzonSellerApiClient;

import java.time.LocalDateTime;

/**
 * Хранение тарифа Ozon Seller ({@code seller/info}) и флага доступности воронки analytics.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OzonSellerSubscriptionService {

    private final OzonSellerApiClient ozonSellerApiClient;
    private final OzonPremiumLkProbeService premiumLkProbeService;
    private final CabinetSyncStateRepository syncStateRepository;

    /**
     * Запрашивает seller/info и сохраняет subscription в {@code cabinet_sync_state}.
     */
    @Transactional
    public void refreshFromApi(Cabinet cabinet) {
        if (cabinet == null || cabinet.getId() == null
                || cabinet.getMarketplaceType() != MarketplaceType.OZON) {
            return;
        }
        String clientId = cabinet.getOzonClientId();
        String apiKey = cabinet.getApiKey();
        if (clientId == null || clientId.isBlank() || apiKey == null || apiKey.isBlank()) {
            return;
        }
        OzonSellerInfoResponse info = ozonSellerApiClient.getSellerInfo(clientId.trim(), apiKey.trim());
        persistFromSellerInfo(cabinet, info);
    }

    /**
     * Сохраняет subscription из уже полученного seller/info.
     */
    @Transactional
    public void persistFromSellerInfo(Cabinet cabinet, OzonSellerInfoResponse info) {
        if (cabinet == null || cabinet.getId() == null) {
            return;
        }
        OzonSellerInfoResponse.Subscription subscription = info != null ? info.resolveSubscription() : null;
        OzonSellerSubscriptionType detectedType =
                OzonSellerInfoResponse.resolveDetectedSubscriptionType(subscription);
        Boolean isPremium = subscription != null ? subscription.getPremium() : null;

        if (shouldProbePremiumLk(subscription, detectedType)) {
            OzonPremiumLkProbeResult probeResult = premiumLkProbeService.probe(
                    cabinet.getOzonClientId(),
                    cabinet.getApiKey()
            );
            log.info(
                    "Ozon subscription cabinetId={}: probe Premium LK via analytics lookback={}",
                    cabinet.getId(),
                    probeResult
            );
            detectedType = applyPremiumLkProbe(detectedType, probeResult);
            isPremium = applyPremiumLkProbeIsPremium(isPremium, probeResult);
        }

        LocalDateTime checkedAt = LocalDateTime.now();

        applyToCabinet(cabinet, detectedType, isPremium, cabinet.getOzonAnalyticsFunnelAvailable(), checkedAt);
        saveSyncState(
                cabinet.getId(),
                detectedType,
                isPremium,
                cabinet.getOzonAnalyticsFunnelAvailable(),
                checkedAt
        );

        log.info(
                "Ozon subscription cabinetId={}: type_={}, typeLegacy={}, rawType={}, sellerInfoType={}, "
                        + "detectedType={}, isPremium={}",
                cabinet.getId(),
                subscription != null ? subscription.getTypeUnderscore() : null,
                subscription != null ? subscription.getTypeLegacy() : null,
                subscription != null ? subscription.resolveTypeRaw() : null,
                subscription != null
                        ? OzonSellerInfoResponse.resolveSubscriptionType(subscription)
                        : OzonSellerSubscriptionType.UNKNOWN,
                detectedType,
                isPremium
        );
    }

    /**
     * Обновляет флаг доступности воронки (probe analytics/data).
     */
    @Transactional
    public void updateFunnelAvailability(Long cabinetId, boolean funnelAvailable) {
        if (cabinetId == null) {
            return;
        }
        CabinetSyncState state = syncStateRepository.findById(cabinetId)
                .orElseGet(() -> CabinetSyncState.builder().cabinetId(cabinetId).build());
        state.setOzonAnalyticsFunnelAvailable(funnelAvailable);
        if (state.getOzonSubscriptionCheckedAt() == null) {
            state.setOzonSubscriptionCheckedAt(LocalDateTime.now());
        }
        syncStateRepository.save(state);
    }

    /**
     * Ручная настройка тарифа Ozon для UI (seller/info не различает Premium в ЛК).
     */
    @Transactional
    public void updateSubscriptionTypeOverride(Long cabinetId, String overrideRaw) {
        if (cabinetId == null) {
            return;
        }
        CabinetSyncState state = syncStateRepository.findById(cabinetId)
                .orElseGet(() -> CabinetSyncState.builder().cabinetId(cabinetId).build());
        if (overrideRaw == null
                || overrideRaw.isBlank()
                || OzonSubscriptionDisplayResolver.OVERRIDE_AUTO.equalsIgnoreCase(overrideRaw.trim())) {
            state.setOzonSubscriptionTypeOverride(null);
        } else {
            OzonSellerSubscriptionType type = OzonSellerSubscriptionType.fromApiValue(overrideRaw.trim());
            if (type == OzonSellerSubscriptionType.UNKNOWN) {
                throw new IllegalArgumentException("Неизвестный тариф Ozon: " + overrideRaw);
            }
            state.setOzonSubscriptionTypeOverride(type.name());
        }
        syncStateRepository.save(state);
    }

    private void saveSyncState(
            Long cabinetId,
            OzonSellerSubscriptionType type,
            Boolean isPremium,
            Boolean funnelAvailable,
            LocalDateTime checkedAt
    ) {
        CabinetSyncState state = syncStateRepository.findById(cabinetId)
                .orElseGet(() -> CabinetSyncState.builder().cabinetId(cabinetId).build());
        state.setOzonSubscriptionType(type != null ? type.name() : null);
        state.setOzonSubscriptionIsPremium(isPremium);
        if (funnelAvailable != null) {
            state.setOzonAnalyticsFunnelAvailable(funnelAvailable);
        }
        state.setOzonSubscriptionCheckedAt(checkedAt);
        syncStateRepository.save(state);
    }

    private static void applyToCabinet(
            Cabinet cabinet,
            OzonSellerSubscriptionType type,
            Boolean isPremium,
            Boolean funnelAvailable,
            LocalDateTime checkedAt
    ) {
        cabinet.setOzonSubscriptionType(type);
        cabinet.setOzonSubscriptionIsPremium(isPremium);
        if (funnelAvailable != null) {
            cabinet.setOzonAnalyticsFunnelAvailable(funnelAvailable);
        }
        cabinet.setOzonSubscriptionCheckedAt(checkedAt);
    }

    /**
     * Probe нужен, когда Ozon не прислал канонический {@code type_} в seller/info.
     */
    private static boolean shouldProbePremiumLk(
            OzonSellerInfoResponse.Subscription subscription,
            OzonSellerSubscriptionType detectedType
    ) {
        if (subscription == null) {
            return false;
        }
        if (subscription.getTypeUnderscore() != null && !subscription.getTypeUnderscore().isBlank()) {
            return false;
        }
        return detectedType == OzonSellerSubscriptionType.UNSPECIFIED
                || detectedType == OzonSellerSubscriptionType.UNKNOWN;
    }

    private static OzonSellerSubscriptionType applyPremiumLkProbe(
            OzonSellerSubscriptionType detectedType,
            OzonPremiumLkProbeResult probeResult
    ) {
        return switch (probeResult) {
            case HAS_PREMIUM -> OzonSellerSubscriptionType.PREMIUM;
            case NO_PREMIUM -> OzonSellerSubscriptionType.UNSPECIFIED;
            case INCONCLUSIVE -> detectedType != null ? detectedType : OzonSellerSubscriptionType.UNSPECIFIED;
        };
    }

    private static Boolean applyPremiumLkProbeIsPremium(
            Boolean sellerInfoIsPremium,
            OzonPremiumLkProbeResult probeResult
    ) {
        return switch (probeResult) {
            case HAS_PREMIUM -> true;
            case NO_PREMIUM -> false;
            case INCONCLUSIVE -> sellerInfoIsPremium;
        };
    }
}
