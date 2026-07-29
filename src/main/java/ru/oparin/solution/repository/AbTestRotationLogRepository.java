package ru.oparin.solution.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.oparin.solution.model.AbTestRotationLog;

import java.util.List;

/**
 * Репозиторий журнала ротаций А/Б-теста.
 */
public interface AbTestRotationLogRepository extends JpaRepository<AbTestRotationLog, Long> {

    List<AbTestRotationLog> findByAbTestIdOrderBySwitchedAtDesc(Long abTestId);
}
