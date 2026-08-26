package ru.oparin.solution.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.oparin.solution.model.*;
import ru.oparin.solution.repository.CabinetIntegrationRepository;
import ru.oparin.solution.repository.CabinetSyncStateRepository;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Phase 5.2: чтение/запись credentials и sync-state через {@code cabinet_integrations} /
 * {@code cabinet_sync_state}. Поля на {@link Cabinet} — in-memory overlay для совместимости call sites.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CabinetIntegrationMirrorService {

    private final CabinetIntegrationRepository integrationRepository;
    private final CabinetSyncStateRepository syncStateRepository;
    private final ObjectMapper objectMapper;

    /**
     * Сохраняет credentials и sync-метки из in-memory {@link Cabinet} в integrations / sync_state.
     */
    @Transactional
    public void persistFromCabinet(Cabinet cabinet) {
        mirrorFromCabinet(cabinet);
    }

    /**
     * @deprecated используйте {@link #persistFromCabinet(Cabinet)}
     */
    @Deprecated
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
     * Dual-read: подгружает integrations и sync-state на entity (in-memory).
     */
    @Transactional(readOnly = true)
    public void overlayOntoCabinet(Cabinet cabinet) {
        if (cabinet == null || cabinet.getId() == null) {
            return;
        }
        overlayOntoCabinets(List.of(cabinet));
    }

    /**
     * Batch overlay для списков кабинетов (меньше round-trips к БД).
     */
    @Transactional(readOnly = true)
    public void overlayOntoCabinets(Collection<Cabinet> cabinets) {
        if (cabinets == null || cabinets.isEmpty()) {
            return;
        }
        List<Long> ids = cabinets.stream()
                .filter(c -> c != null && c.getId() != null)
                .map(Cabinet::getId)
                .distinct()
                .toList();
        if (ids.isEmpty()) {
            return;
        }
        try {
            Map<Long, List<CabinetIntegration>> integrationsByCabinet = integrationRepository.findByCabinetIdIn(ids)
                    .stream()
                    .collect(Collectors.groupingBy(CabinetIntegration::getCabinetId));
            Map<Long, CabinetSyncState> syncByCabinet = syncStateRepository.findAllById(ids).stream()
                    .collect(Collectors.toMap(CabinetSyncState::getCabinetId, Function.identity()));

            for (Cabinet cabinet : cabinets) {
                if (cabinet == null || cabinet.getId() == null) {
                    continue;
                }
                applyIntegrations(cabinet, integrationsByCabinet.getOrDefault(cabinet.getId(), List.of()));
                CabinetSyncState syncState = syncByCabinet.get(cabinet.getId());
                if (syncState != null) {
                    applySyncState(cabinet, syncState);
                }
            }
        } catch (Exception e) {
            log.debug("Cabinet integration batch overlay failed: {}", e.getMessage());
        }
    }

    private void applyIntegrations(Cabinet cabinet, List<CabinetIntegration> integrations) {
        if (cabinet.getMarketplaceType() == MarketplaceType.WB) {
            integrations.stream()
                    .filter(i -> i.getIntegrationType() == CabinetIntegrationType.WB_API)
                    .findFirst()
                    .ifPresent(i -> applyWb(cabinet, i));
        } else if (cabinet.getMarketplaceType() == MarketplaceType.OZON) {
            integrations.stream()
                    .filter(i -> i.getIntegrationType() == CabinetIntegrationType.OZON_SELLER)
                    .findFirst()
                    .ifPresent(i -> applyOzonSeller(cabinet, i));
            integrations.stream()
                    .filter(i -> i.getIntegrationType() == CabinetIntegrationType.OZON_PERFORMANCE)
                    .findFirst()
                    .ifPresent(i -> applyOzonPerformance(cabinet, i));
        }
    }

    /**
     * Определяет тип WB-токена по API-ключу (Phase 5.2 — из {@code cabinet_integrations}).
     */
    @Transactional(readOnly = true)
    public CabinetTokenType resolveWbTokenTypeByApiKey(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            return CabinetTokenType.BASIC;
        }
        return integrationRepository
                .findTopByCredentialPrimaryAndIntegrationTypeOrderByCabinetIdDesc(
                        apiKey.trim(), CabinetIntegrationType.WB_API)
                .map(i -> {
                    CabinetTokenType tokenType = readTokenType(i.getMetaJson());
                    return tokenType != null ? tokenType : CabinetTokenType.BASIC;
                })
                .orElse(CabinetTokenType.BASIC);
    }

    private void upsertWbIntegration(Cabinet cabinet) {
        CabinetIntegration row = integrationRepository
                .findByCabinetIdAndIntegrationType(cabinet.getId(), CabinetIntegrationType.WB_API)
                .orElseGet(() -> CabinetIntegration.builder()
                        .cabinetId(cabinet.getId())
                        .integrationType(CabinetIntegrationType.WB_API)
                        .build());
        if (notBlank(cabinet.getApiKey())) {
            row.setCredentialPrimary(cabinet.getApiKey());
        }
        if (cabinet.getTokenType() != null) {
            row.setMetaJson(tokenTypeMeta(cabinet.getTokenType()));
        }
        if (cabinet.getIsValid() != null) {
            row.setIsValid(cabinet.getIsValid());
        }
        if (cabinet.getLastValidatedAt() != null) {
            row.setLastValidatedAt(cabinet.getLastValidatedAt());
        }
        if (cabinet.getValidationError() != null || Boolean.TRUE.equals(cabinet.getIsValid())) {
            row.setValidationError(cabinet.getValidationError());
        }
        integrationRepository.save(row);
    }

    private void upsertOzonSellerIntegration(Cabinet cabinet) {
        CabinetIntegration row = integrationRepository
                .findByCabinetIdAndIntegrationType(cabinet.getId(), CabinetIntegrationType.OZON_SELLER)
                .orElseGet(() -> CabinetIntegration.builder()
                        .cabinetId(cabinet.getId())
                        .integrationType(CabinetIntegrationType.OZON_SELLER)
                        .build());
        if (notBlank(cabinet.getApiKey())) {
            row.setCredentialPrimary(cabinet.getApiKey());
        }
        if (notBlank(cabinet.getOzonClientId())) {
            row.setCredentialSecondary(cabinet.getOzonClientId());
        }
        if (cabinet.getIsValid() != null) {
            row.setIsValid(cabinet.getIsValid());
        }
        if (cabinet.getLastValidatedAt() != null) {
            row.setLastValidatedAt(cabinet.getLastValidatedAt());
        }
        if (cabinet.getValidationError() != null || Boolean.TRUE.equals(cabinet.getIsValid())) {
            row.setValidationError(cabinet.getValidationError());
        }
        integrationRepository.save(row);
    }

    private void upsertOzonPerformanceIntegration(Cabinet cabinet) {
        boolean hasCreds = notBlank(cabinet.getOzonPerformanceClientId())
                || notBlank(cabinet.getOzonPerformanceClientSecret());
        Optional<CabinetIntegration> existing = integrationRepository
                .findByCabinetIdAndIntegrationType(cabinet.getId(), CabinetIntegrationType.OZON_PERFORMANCE);
        if (!hasCreds) {
            if (existing.isEmpty()) {
                return;
            }
            CabinetIntegration row = existing.get();
            if (!notBlank(row.getCredentialPrimary()) && !notBlank(row.getCredentialSecondary())) {
                integrationRepository.delete(row);
                return;
            }
            applyOzonPerformanceValidation(row, cabinet);
            integrationRepository.save(row);
            return;
        }
        CabinetIntegration row = existing.orElseGet(() -> CabinetIntegration.builder()
                .cabinetId(cabinet.getId())
                .integrationType(CabinetIntegrationType.OZON_PERFORMANCE)
                .build());
        if (notBlank(cabinet.getOzonPerformanceClientSecret())) {
            row.setCredentialPrimary(cabinet.getOzonPerformanceClientSecret());
        }
        if (notBlank(cabinet.getOzonPerformanceClientId())) {
            row.setCredentialSecondary(cabinet.getOzonPerformanceClientId());
        }
        applyOzonPerformanceValidation(row, cabinet);
        integrationRepository.save(row);
    }

    private void applyOzonPerformanceValidation(CabinetIntegration row, Cabinet cabinet) {
        if (cabinet.getOzonPerformanceIsValid() != null) {
            row.setIsValid(cabinet.getOzonPerformanceIsValid());
        }
        if (cabinet.getOzonPerformanceLastValidatedAt() != null) {
            row.setLastValidatedAt(cabinet.getOzonPerformanceLastValidatedAt());
        }
        if (cabinet.getOzonPerformanceValidationError() != null
                || Boolean.TRUE.equals(cabinet.getOzonPerformanceIsValid())) {
            row.setValidationError(cabinet.getOzonPerformanceValidationError());
        }
    }

    private void upsertSyncState(Cabinet cabinet) {
        CabinetSyncState state = syncStateRepository.findById(cabinet.getId())
                .orElseGet(() -> CabinetSyncState.builder().cabinetId(cabinet.getId()).build());
        if (cabinet.getLastDataUpdateAt() != null) {
            state.setLastDataUpdateAt(cabinet.getLastDataUpdateAt());
        }
        if (cabinet.getLastDataUpdateRequestedAt() != null) {
            state.setLastDataUpdateRequestedAt(cabinet.getLastDataUpdateRequestedAt());
        }
        if (cabinet.getLastStocksUpdateAt() != null) {
            state.setLastStocksUpdateAt(cabinet.getLastStocksUpdateAt());
        }
        if (cabinet.getLastStocksUpdateRequestedAt() != null) {
            state.setLastStocksUpdateRequestedAt(cabinet.getLastStocksUpdateRequestedAt());
        }
        if (cabinet.getLastOzonCampaignsSyncAt() != null) {
            state.setLastOzonCampaignsSyncAt(cabinet.getLastOzonCampaignsSyncAt());
        }
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
