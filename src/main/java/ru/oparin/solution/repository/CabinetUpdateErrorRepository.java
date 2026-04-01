package ru.oparin.solution.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.oparin.solution.model.CabinetUpdateError;

import java.util.List;

public interface CabinetUpdateErrorRepository extends JpaRepository<CabinetUpdateError, Long> {
    List<CabinetUpdateError> findByCabinetIdOrderByOccurredAtDesc(Long cabinetId);
}
