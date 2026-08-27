package ru.oparin.solution.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.oparin.solution.dto.ozon.OzonProductListResponse;
import ru.oparin.solution.dto.ozon.OzonSellerInfoResponse;
import ru.oparin.solution.model.Cabinet;
import ru.oparin.solution.model.CabinetSyncState;
import ru.oparin.solution.model.MarketplaceType;
import ru.oparin.solution.model.OzonSellerSubscriptionType;
import ru.oparin.solution.repository.CabinetSyncStateRepository;
import ru.oparin.solution.repository.OzonProductCardRepository;
import ru.oparin.solution.service.ozon.OzonProductsApiClient;
import ru.oparin.solution.service.ozon.OzonSellerApiClient;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Хранение тарифа Ozon Seller ({@code seller/info}) и флага доступности воронки analytics.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OzonSellerSubscriptionService {

    private final OzonSellerApiClient ozonSellerApiClient;
    private final OzonProductsApiClient ozonProductsApiClient;
    private final CabinetSyncStateRepository syncStateRepository;
    private final OzonProductCardRepository ozonProductCardRepository;

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
        String trimmedClientId = clientId.trim();
        String trimmedApiKey = apiKey.trim();
        OzonSellerInfoResponse info = ozonSellerApiClient.getSellerInfo(trimmedClientId, trimmedApiKey);
        persistFromSellerInfo(cabinet, info, trimmedClientId, trimmedApiKey);
    }

    /**
     * Сохраняет subscription из уже полученного seller/info.
     */
    @Transactional
    public void persistFromSellerInfo(Cabinet cabinet, OzonSellerInfoResponse info) {
        if (cabinet == null || cabinet.getId() == null) {
            return;
        }
        String clientId = cabinet.getOzonClientId();
        String apiKey = cabinet.getApiKey();
        if (clientId == null || clientId.isBlank() || apiKey == null || apiKey.isBlank()) {
            persistResolvedSubscription(cabinet, info, OzonSellerSubscriptionType.UNKNOWN, null);
            return;
        }
        persistFromSellerInfo(cabinet, info, clientId.trim(), apiKey.trim());
    }

    private void persistFromSellerInfo(
            Cabinet cabinet,
            OzonSellerInfoResponse info,
            String clientId,
            String apiKey
    ) {
        OzonSellerInfoResponse.Subscription subscription = info != null ? info.resolveSubscription() : null;
        OzonSellerSubscriptionType sellerInfoType =
                OzonSellerInfoResponse.resolveSubscriptionType(subscription);
        Boolean sellerInfoPremium = subscription != null ? subscription.getPremium() : null;

        ResolvedSubscription resolved = resolveWithPremiumProbe(
                cabinet.getId(),
                clientId,
                apiKey,
                sellerInfoType,
                sellerInfoPremium
        );
        persistResolvedSubscription(
                cabinet,
                info,
                resolved.type(),
                resolved.isPremium()
        );
    }

    private void persistResolvedSubscription(
            Cabinet cabinet,
            OzonSellerInfoResponse info,
            OzonSellerSubscriptionType type,
            Boolean isPremium
    ) {
        OzonSellerInfoResponse.Subscription subscription = info != null ? info.resolveSubscription() : null;
        LocalDateTime checkedAt = LocalDateTime.now();

        applyToCabinet(cabinet, type, isPremium, cabinet.getOzonAnalyticsFunnelAvailable(), checkedAt);
        saveSyncState(cabinet.getId(), type, isPremium, cabinet.getOzonAnalyticsFunnelAvailable(), checkedAt);

        log.info(
                "Ozon subscription cabinetId={}: type_={}, typeLegacy={}, rawType={}, sellerInfoType={}, "
                        + "resolvedType={}, isPremium={}",
                cabinet.getId(),
                subscription != null ? subscription.getTypeUnderscore() : null,
                subscription != null ? subscription.getTypeLegacy() : null,
                subscription != null ? subscription.resolveTypeRaw() : null,
                subscription != null
                        ? OzonSellerInfoResponse.resolveSubscriptionType(subscription)
                        : OzonSellerSubscriptionType.UNKNOWN,
                type,
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

    private ResolvedSubscription resolveWithPremiumProbe(
            Long cabinetId,
            String clientId,
            String apiKey,
            OzonSellerSubscriptionType sellerInfoType,
            Boolean sellerInfoPremium
    ) {
        if (sellerInfoType == OzonSellerSubscriptionType.UNSPECIFIED
                || sellerInfoType == OzonSellerSubscriptionType.UNKNOWN) {
            return new ResolvedSubscription(OzonSellerSubscriptionType.UNSPECIFIED, false);
        }

        Boolean premiumProbe = probePremiumLkAccess(cabinetId, clientId, apiKey);
        if (premiumProbe != null) {
            if (premiumProbe) {
                return new ResolvedSubscription(sellerInfoType, true);
            }
            log.info(
                    "Ozon subscription cabinetId={}: seller/info={} is_premium={}, probe Premium=нет → Без Premium",
                    cabinetId,
                    sellerInfoType,
                    sellerInfoPremium
            );
            return new ResolvedSubscription(OzonSellerSubscriptionType.UNSPECIFIED, false);
        }

        // Ozon seller/info часто отдаёт type=PREMIUM всем; без probe не понижаем до «Без Premium».
        log.info(
                "Ozon subscription cabinetId={}: probe Premium неоднозначен, seller/info={} (is_premium={})",
                cabinetId,
                sellerInfoType,
                sellerInfoPremium
        );
        if (Boolean.FALSE.equals(sellerInfoPremium)) {
            return new ResolvedSubscription(OzonSellerSubscriptionType.UNSPECIFIED, false);
        }
        return new ResolvedSubscription(sellerInfoType, true);
    }

    /**
     * @return {@code true} — Premium подтверждён, {@code false} — бесплатный тариф, {@code null} — неизвестно
     */
    private Boolean probePremiumLkAccess(
            Long cabinetId,
            String clientId,
            String apiKey
    ) {
        OzonProductsApiClient.PremiumLkProbeResult viaAnalytics =
                ozonProductsApiClient.probePremiumLkViaAnalyticsDateRange(clientId, apiKey);
        log.info("Ozon subscription cabinetId={}: probe Premium via analytics date range={}",
                cabinetId, viaAnalytics);
        if (viaAnalytics == OzonProductsApiClient.PremiumLkProbeResult.HAS_PREMIUM) {
            return true;
        }
        if (viaAnalytics == OzonProductsApiClient.PremiumLkProbeResult.NO_PREMIUM) {
            return false;
        }

        for (Long sku : findProbeSkus(cabinetId, clientId, apiKey)) {
            OzonProductsApiClient.PremiumLkProbeResult viaQueries =
                    ozonProductsApiClient.probePremiumLkViaProductQueriesSort(clientId, apiKey, sku);
            log.info("Ozon subscription cabinetId={}: probe Premium via product-queries BY_VIEWS={}, SKU={}",
                    cabinetId, viaQueries, sku);
            if (viaQueries == OzonProductsApiClient.PremiumLkProbeResult.HAS_PREMIUM) {
                return true;
            }
            if (viaQueries == OzonProductsApiClient.PremiumLkProbeResult.NO_PREMIUM) {
                return false;
            }
        }

        log.info("Ozon subscription cabinetId={}: probe Premium не дал однозначного ответа", cabinetId);
        return null;
    }

    private List<Long> findProbeSkus(Long cabinetId, String clientId, String apiKey) {
        List<Long> skus = new java.util.ArrayList<>();
        List<Long> skusFromDb = ozonProductCardRepository.findSkusByCabinetId(cabinetId, PageRequest.of(0, 3));
        if (skusFromDb != null) {
            skusFromDb.stream().filter(sku -> sku != null).forEach(skus::add);
        }
        if (!skus.isEmpty()) {
            return skus;
        }

        try {
            OzonProductListResponse response = ozonProductsApiClient.listProducts(clientId, apiKey, "", 3);
            if (response == null || response.getResult() == null) {
                return skus;
            }
            List<OzonProductListResponse.Item> items = response.getResult().getItems();
            if (items == null) {
                return skus;
            }
            for (OzonProductListResponse.Item item : items) {
                if (item != null && item.getSku() != null) {
                    skus.add(item.getSku());
                }
            }
        } catch (Exception e) {
            log.warn("Ozon subscription probe: не удалось получить SKU из product/list: {}", e.getMessage());
        }
        return skus;
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

    private record ResolvedSubscription(OzonSellerSubscriptionType type, Boolean isPremium) {
    }
}
