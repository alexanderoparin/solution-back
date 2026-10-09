package ru.oparin.solution.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import ru.oparin.solution.model.WbHypothesis;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Репозиторий гипотез.
 */
public interface WbHypothesisRepository extends JpaRepository<WbHypothesis, Long>, JpaSpecificationExecutor<WbHypothesis> {

    Optional<WbHypothesis> findByIdAndCabinetId(Long id, Long cabinetId);

    List<WbHypothesis> findByCabinetIdAndIdIn(Long cabinetId, Collection<Long> ids);

    long deleteByCabinetIdAndIdIn(Long cabinetId, Collection<Long> ids);
}
