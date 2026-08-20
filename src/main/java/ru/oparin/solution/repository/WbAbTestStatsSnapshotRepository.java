package ru.oparin.solution.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.oparin.solution.model.WbAbTestStatsSnapshot;

import java.util.List;
import java.util.Optional;

/**
 * Репозиторий снимков статистики для А/Б-тестов.
 */
public interface WbAbTestStatsSnapshotRepository extends JpaRepository<WbAbTestStatsSnapshot, Long> {

    List<WbAbTestStatsSnapshot> findByAbTestId(Long abTestId);

    Optional<WbAbTestStatsSnapshot> findByAbTestIdAndAdvertIdAndNmId(Long abTestId, Long advertId, Long nmId);

    void deleteByAbTestId(Long abTestId);
}
