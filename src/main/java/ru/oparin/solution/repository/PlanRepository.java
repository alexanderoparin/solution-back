package ru.oparin.solution.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.oparin.solution.model.Plan;

import java.util.List;
import java.util.Optional;

@Repository
public interface PlanRepository extends JpaRepository<Plan, Long> {

    List<Plan> findByIsActiveTrueOrderBySortOrderAsc();

    List<Plan> findAllByOrderBySortOrderAsc();

    Optional<Plan> findByCode(String code);
}

