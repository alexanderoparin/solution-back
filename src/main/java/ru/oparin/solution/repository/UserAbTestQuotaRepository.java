package ru.oparin.solution.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.oparin.solution.model.UserAbTestQuota;

/**
 * Репозиторий пользовательской квоты А/Б.
 */
public interface UserAbTestQuotaRepository extends JpaRepository<UserAbTestQuota, Long> {
}
