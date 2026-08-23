package ru.oparin.solution.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.oparin.solution.model.Cabinet;
import ru.oparin.solution.model.CabinetSyncState;
import ru.oparin.solution.repository.CabinetSyncStateRepository;

import java.time.LocalDateTime;
import java.util.function.Consumer;

/**
 * Единая точка записи меток синхронизации кабинета (Phase 5.2 cutover).
 * Канон — таблица {@code cabinet_sync_state}; поля на {@link Cabinet} заполняются in-memory для совместимости call sites.
 */
@Service
@RequiredArgsConstructor
public class CabinetSyncStateService {

    private final CabinetSyncStateRepository syncStateRepository;

    @Transactional
    public void touchLastDataUpdateAt(Long cabinetId) {
        update(cabinetId, state -> state.setLastDataUpdateAt(LocalDateTime.now()));
    }

    @Transactional
    public void touchLastDataUpdateAt(Cabinet cabinet) {
        if (cabinet == null || cabinet.getId() == null) {
            return;
        }
        touchLastDataUpdateAt(cabinet.getId());
        cabinet.setLastDataUpdateAt(LocalDateTime.now());
    }

    @Transactional
    public void touchLastDataUpdateRequestedAt(Long cabinetId) {
        update(cabinetId, state -> state.setLastDataUpdateRequestedAt(LocalDateTime.now()));
    }

    @Transactional
    public void touchLastDataUpdateRequestedAt(Cabinet cabinet) {
        if (cabinet == null || cabinet.getId() == null) {
            return;
        }
        touchLastDataUpdateRequestedAt(cabinet.getId());
        cabinet.setLastDataUpdateRequestedAt(LocalDateTime.now());
    }

    @Transactional
    public void touchLastStocksUpdateAt(Long cabinetId) {
        update(cabinetId, state -> state.setLastStocksUpdateAt(LocalDateTime.now()));
    }

    @Transactional
    public void touchLastStocksUpdateAt(Cabinet cabinet) {
        if (cabinet == null || cabinet.getId() == null) {
            return;
        }
        touchLastStocksUpdateAt(cabinet.getId());
        cabinet.setLastStocksUpdateAt(LocalDateTime.now());
    }

    @Transactional
    public void touchLastStocksUpdateRequestedAt(Long cabinetId) {
        update(cabinetId, state -> state.setLastStocksUpdateRequestedAt(LocalDateTime.now()));
    }

    @Transactional
    public void touchLastStocksUpdateRequestedAt(Cabinet cabinet) {
        if (cabinet == null || cabinet.getId() == null) {
            return;
        }
        touchLastStocksUpdateRequestedAt(cabinet.getId());
        cabinet.setLastStocksUpdateRequestedAt(LocalDateTime.now());
    }

    @Transactional
    public void touchLastOzonCampaignsSyncAt(Long cabinetId) {
        update(cabinetId, state -> state.setLastOzonCampaignsSyncAt(LocalDateTime.now()));
    }

    @Transactional
    public void touchLastOzonCampaignsSyncAt(Cabinet cabinet) {
        if (cabinet == null || cabinet.getId() == null) {
            return;
        }
        touchLastOzonCampaignsSyncAt(cabinet.getId());
        cabinet.setLastOzonCampaignsSyncAt(LocalDateTime.now());
    }

    private void update(Long cabinetId, Consumer<CabinetSyncState> mutator) {
        CabinetSyncState state = syncStateRepository.findById(cabinetId)
                .orElseGet(() -> CabinetSyncState.builder().cabinetId(cabinetId).build());
        mutator.accept(state);
        syncStateRepository.save(state);
    }
}
