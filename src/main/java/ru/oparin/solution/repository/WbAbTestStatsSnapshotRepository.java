package ru.oparin.solution.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.oparin.solution.model.WbAbTestStatsSnapshot;

import java.util.List;
import java.util.Optional;

/**
 * Репозиторий снимков статистики для А/Б-тестов.
 */
public interface WbAbTestStatsSnapshotRepository extends JpaRepository<WbAbTestStatsSnapshot, Long> {

    List<WbAbTestStatsSnapshot> findByWbAbTestId(Long abTestId);

    Optional<WbAbTestStatsSnapshot> findByWbAbTestIdAndAdvertIdAndNmId(Long abTestId, Long advertId, Long nmId);

    void deleteByWbAbTestId(Long abTestId);
}
