package ru.oparin.solution.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.oparin.solution.model.*;
import ru.oparin.solution.repository.CabinetIntegrationRepository;
import ru.oparin.solution.repository.CabinetSyncStateRepository;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Phase 5: dual-write / dual-read между колонками {@link Cabinet} и
 * {@code cabinet_integrations} / {@code cabinet_sync_state}.
 * <p>
 * До cutover колонки cabinets остаются каноном для большинства call sites;
 * этот сервис зеркалит записи и при чтении может подтянуть значения из integrations.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CabinetIntegrationMirrorService {

    private final CabinetIntegrationRepository integrationRepository;
    private final CabinetSyncStateRepository syncStateRepository;
    private final ObjectMapper objectMapper;

    /**
     * Зеркалит credentials и sync-метки кабинета в новые таблицы.
     */
    @Transactional
    public void mirrorFromCabinet(Cabinet cabinet) {
        if (cabinet == null || cabinet.getId() == null) {
            return;
        }
        try {
            if (cabinet.getMarketplaceType() == MarketplaceType.WB) {
                upsertWbIntegration(cabinet);
            } else if (cabinet.getMarketplaceType() == MarketplaceType.OZON) {
                upsertOzonSellerIntegration(cabinet);
                upsertOzonPerformanceIntegration(cabinet);
            }
            upsertSyncState(cabinet);
        } catch (Exception e) {
            // Не ломаем основной save кабинета из‑за зеркала.
            log.warn("Cabinet integration mirror failed for cabinetId={}: {}", cabinet.getId(), e.getMessage());
        }
    }

    /**
     * Dual-read: если в integrations есть значения — накладывает их на entity (in-memory).
     * Колонки cabinets не перезаписываются в БД.
     */
    @Transactional(readOnly = true)
    public void overlayOntoCabinet(Cabinet cabinet) {
        if (cabinet == null || cabinet.getId() == null) {
            return;
        }
        try {
            if (cabinet.getMarketplaceType() == MarketplaceType.WB) {
                integrationRepository.findByCabinetIdAndIntegrationType(cabinet.getId(), CabinetIntegrationType.WB_API)
                        .ifPresent(i -> applyWb(cabinet, i));
            } else if (cabinet.getMarketplaceType() == MarketplaceType.OZON) {
                integrationRepository.findByCabinetIdAndIntegrationType(cabinet.getId(), CabinetIntegrationType.OZON_SELLER)
                        .ifPresent(i -> applyOzonSeller(cabinet, i));
                integrationRepository.findByCabinetIdAndIntegrationType(cabinet.getId(), CabinetIntegrationType.OZON_PERFORMANCE)
                        .ifPresent(i -> applyOzonPerformance(cabinet, i));
            }
            syncStateRepository.findById(cabinet.getId()).ifPresent(s -> applySyncState(cabinet, s));
        } catch (Exception e) {
            log.debug("Cabinet integration overlay skipped for cabinetId={}: {}", cabinet.getId(), e.getMessage());
        }
    }

    private void upsertWbIntegration(Cabinet cabinet) {
        CabinetIntegration row = integrationRepository
                .findByCabinetIdAndIntegrationType(cabinet.getId(), CabinetIntegrationType.WB_API)
                .orElseGet(() -> CabinetIntegration.builder()
                        .cabinetId(cabinet.getId())
                        .integrationType(CabinetIntegrationType.WB_API)
                        .build());
        row.setCredentialPrimary(cabinet.getApiKey());
        row.setCredentialSecondary(null);
        row.setMetaJson(tokenTypeMeta(cabinet.getTokenType()));
        row.setIsValid(cabinet.getIsValid());
        row.setLastValidatedAt(cabinet.getLastValidatedAt());
        row.setValidationError(cabinet.getValidationError());
        integrationRepository.save(row);
    }

    private void upsertOzonSellerIntegration(Cabinet cabinet) {
        CabinetIntegration row = integrationRepository
                .findByCabinetIdAndIntegrationType(cabinet.getId(), CabinetIntegrationType.OZON_SELLER)
                .orElseGet(() -> CabinetIntegration.builder()
                        .cabinetId(cabinet.getId())
                        .integrationType(CabinetIntegrationType.OZON_SELLER)
                        .build());
        row.setCredentialPrimary(cabinet.getApiKey());
        row.setCredentialSecondary(cabinet.getOzonClientId());
        row.setMetaJson(null);
        row.setIsValid(cabinet.getIsValid());
        row.setLastValidatedAt(cabinet.getLastValidatedAt());
        row.setValidationError(cabinet.getValidationError());
        integrationRepository.save(row);
    }

    private void upsertOzonPerformanceIntegration(Cabinet cabinet) {
        boolean hasCreds = notBlank(cabinet.getOzonPerformanceClientId())
                || notBlank(cabinet.getOzonPerformanceClientSecret());
        Optional<CabinetIntegration> existing = integrationRepository
                .findByCabinetIdAndIntegrationType(cabinet.getId(), CabinetIntegrationType.OZON_PERFORMANCE);
        if (!hasCreds) {
            existing.ifPresent(integrationRepository::delete);
            return;
        }
        CabinetIntegration row = existing.orElseGet(() -> CabinetIntegration.builder()
                .cabinetId(cabinet.getId())
                .integrationType(CabinetIntegrationType.OZON_PERFORMANCE)
                .build());
        row.setCredentialPrimary(cabinet.getOzonPerformanceClientSecret());
        row.setCredentialSecondary(cabinet.getOzonPerformanceClientId());
        row.setMetaJson(null);
        row.setIsValid(cabinet.getOzonPerformanceIsValid());
        row.setLastValidatedAt(cabinet.getOzonPerformanceLastValidatedAt());
        row.setValidationError(cabinet.getOzonPerformanceValidationError());
        integrationRepository.save(row);
    }

    private void upsertSyncState(Cabinet cabinet) {
        CabinetSyncState state = syncStateRepository.findById(cabinet.getId())
                .orElseGet(() -> CabinetSyncState.builder().cabinetId(cabinet.getId()).build());
        state.setLastDataUpdateAt(cabinet.getLastDataUpdateAt());
        state.setLastDataUpdateRequestedAt(cabinet.getLastDataUpdateRequestedAt());
        state.setLastStocksUpdateAt(cabinet.getLastStocksUpdateAt());
        state.setLastStocksUpdateRequestedAt(cabinet.getLastStocksUpdateRequestedAt());
        state.setLastOzonCampaignsSyncAt(cabinet.getLastOzonCampaignsSyncAt());
        syncStateRepository.save(state);
    }

    private void applyWb(Cabinet cabinet, CabinetIntegration i) {
        if (notBlank(i.getCredentialPrimary())) {
            cabinet.setApiKey(i.getCredentialPrimary());
        }
        if (i.getIsValid() != null) {
            cabinet.setIsValid(i.getIsValid());
        }
        if (i.getLastValidatedAt() != null) {
            cabinet.setLastValidatedAt(i.getLastValidatedAt());
        }
        cabinet.setValidationError(i.getValidationError());
        CabinetTokenType tokenType = readTokenType(i.getMetaJson());
        if (tokenType != null) {
            cabinet.setTokenType(tokenType);
        }
    }

    private void applyOzonSeller(Cabinet cabinet, CabinetIntegration i) {
        if (notBlank(i.getCredentialPrimary())) {
            cabinet.setApiKey(i.getCredentialPrimary());
        }
        if (notBlank(i.getCredentialSecondary())) {
            cabinet.setOzonClientId(i.getCredentialSecondary());
        }
        if (i.getIsValid() != null) {
            cabinet.setIsValid(i.getIsValid());
        }
        if (i.getLastValidatedAt() != null) {
            cabinet.setLastValidatedAt(i.getLastValidatedAt());
        }
        cabinet.setValidationError(i.getValidationError());
    }

    private void applyOzonPerformance(Cabinet cabinet, CabinetIntegration i) {
        if (notBlank(i.getCredentialPrimary())) {
            cabinet.setOzonPerformanceClientSecret(i.getCredentialPrimary());
        }
        if (notBlank(i.getCredentialSecondary())) {
            cabinet.setOzonPerformanceClientId(i.getCredentialSecondary());
        }
        if (i.getIsValid() != null) {
            cabinet.setOzonPerformanceIsValid(i.getIsValid());
        }
        if (i.getLastValidatedAt() != null) {
            cabinet.setOzonPerformanceLastValidatedAt(i.getLastValidatedAt());
        }
        cabinet.setOzonPerformanceValidationError(i.getValidationError());
    }

    private void applySyncState(Cabinet cabinet, CabinetSyncState s) {
        if (s.getLastDataUpdateAt() != null) {
            cabinet.setLastDataUpdateAt(s.getLastDataUpdateAt());
        }
        if (s.getLastDataUpdateRequestedAt() != null) {
            cabinet.setLastDataUpdateRequestedAt(s.getLastDataUpdateRequestedAt());
        }
        if (s.getLastStocksUpdateAt() != null) {
            cabinet.setLastStocksUpdateAt(s.getLastStocksUpdateAt());
        }
        if (s.getLastStocksUpdateRequestedAt() != null) {
            cabinet.setLastStocksUpdateRequestedAt(s.getLastStocksUpdateRequestedAt());
        }
        if (s.getLastOzonCampaignsSyncAt() != null) {
            cabinet.setLastOzonCampaignsSyncAt(s.getLastOzonCampaignsSyncAt());
        }
    }

    private String tokenTypeMeta(CabinetTokenType tokenType) {
        if (tokenType == null) {
            return null;
        }
        try {
            Map<String, String> map = new HashMap<>();
            map.put("tokenType", tokenType.name());
            return objectMapper.writeValueAsString(map);
        } catch (Exception e) {
            return null;
        }
    }

    private CabinetTokenType readTokenType(String metaJson) {
        if (metaJson == null || metaJson.isBlank()) {
            return null;
        }
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = objectMapper.readValue(metaJson, Map.class);
            Object raw = map.get("tokenType");
            if (raw == null) {
                return null;
            }
            return CabinetTokenType.valueOf(raw.toString());
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
