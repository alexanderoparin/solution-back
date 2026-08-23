package ru.oparin.solution.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.oparin.solution.model.CabinetSyncState;

/**
 * Репозиторий меток синхронизации кабинета.
 */
@Repository
public interface CabinetSyncStateRepository extends JpaRepository<CabinetSyncState, Long> {
}
