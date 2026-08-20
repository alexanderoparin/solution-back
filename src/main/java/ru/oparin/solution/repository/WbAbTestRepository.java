package ru.oparin.solution.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.oparin.solution.model.WbAbTest;
import ru.oparin.solution.model.WbAbTestStatus;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Репозиторий А/Б-тестов главного фото.
 */
public interface WbAbTestRepository extends JpaRepository<WbAbTest, Long> {

    List<WbAbTest> findByCabinetIdOrderByCreatedAtDesc(Long cabinetId);

    List<WbAbTest> findByCabinetIdAndStatusOrderByCreatedAtDesc(Long cabinetId, WbAbTestStatus status);

    Optional<WbAbTest> findByIdAndCabinetId(Long id, Long cabinetId);

    List<WbAbTest> findByStatus(WbAbTestStatus status);

    List<WbAbTest> findByStatusIn(Collection<WbAbTestStatus> statuses);

    boolean existsByCabinetIdAndNmIdAndStatus(Long cabinetId, Long nmId, WbAbTestStatus status);

    boolean existsByCabinetIdAndNmIdAndStatusIn(Long cabinetId, Long nmId, Collection<WbAbTestStatus> statuses);
}
