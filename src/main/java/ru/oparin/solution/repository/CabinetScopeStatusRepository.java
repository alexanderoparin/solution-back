package ru.oparin.solution.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.oparin.solution.model.CabinetScopeStatus;

import java.util.List;
import java.util.Optional;

public interface CabinetScopeStatusRepository extends JpaRepository<CabinetScopeStatus, Long> {

    List<CabinetScopeStatus> findByCabinetIdOrderByCategory(Long cabinetId);

    Optional<CabinetScopeStatus> findByCabinetIdAndCategory(Long cabinetId, String category);
}
