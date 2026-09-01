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
    private final CabinetSyncStateRepository syncStateRepository;

    /**
     * Запрашивает seller/info и сохраняет subscription в {@code cabinet_sync_state}.
     */
    @Transactional
    public void refreshFromApi(Cabinet cabinet) {
        refreshSellerInfoFromApi(cabinet);
    }

    /**
     * Только seller/info.
     */
    @Transactional
    public void refreshSellerInfoFromApi(Cabinet cabinet) {
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
     * Ручная настройка тарифа Ozon для UI.
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
}
