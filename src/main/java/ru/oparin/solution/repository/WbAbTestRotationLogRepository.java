package ru.oparin.solution.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.oparin.solution.model.WbAbTestRotationLog;

import java.util.List;

/**
 * Репозиторий журнала ротаций А/Б-теста.
 */
public interface WbAbTestRotationLogRepository extends JpaRepository<WbAbTestRotationLog, Long> {

    List<WbAbTestRotationLog> findByAbTestIdOrderBySwitchedAtDesc(Long abTestId);
}
