package ru.oparin.solution.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.oparin.solution.model.AbTestStatsSnapshot;

import java.util.List;
import java.util.Optional;

/**
 * Репозиторий снимков статистики для А/Б-тестов.
 */
public interface AbTestStatsSnapshotRepository extends JpaRepository<AbTestStatsSnapshot, Long> {

    List<AbTestStatsSnapshot> findByAbTestId(Long abTestId);

    Optional<AbTestStatsSnapshot> findByAbTestIdAndAdvertIdAndNmId(Long abTestId, Long advertId, Long nmId);

    void deleteByAbTestId(Long abTestId);
}
