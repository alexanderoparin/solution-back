package ru.oparin.solution.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.oparin.solution.model.AbTest;
import ru.oparin.solution.model.AbTestStatus;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Репозиторий А/Б-тестов главного фото.
 */
public interface AbTestRepository extends JpaRepository<AbTest, Long> {

    List<AbTest> findByCabinetIdOrderByCreatedAtDesc(Long cabinetId);

    List<AbTest> findByCabinetIdAndStatusOrderByCreatedAtDesc(Long cabinetId, AbTestStatus status);

    Optional<AbTest> findByIdAndCabinetId(Long id, Long cabinetId);

    List<AbTest> findByStatus(AbTestStatus status);

    List<AbTest> findByStatusIn(Collection<AbTestStatus> statuses);

    boolean existsByCabinetIdAndNmIdAndStatus(Long cabinetId, Long nmId, AbTestStatus status);

    boolean existsByCabinetIdAndNmIdAndStatusIn(Long cabinetId, Long nmId, Collection<AbTestStatus> statuses);
}
