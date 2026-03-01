package ru.oparin.solution.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.oparin.solution.model.PasswordResetToken;

import java.time.Instant;
import java.util.Optional;

/**
 * Репозиторий токенов сброса пароля.
 */
@Repository
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    Optional<PasswordResetToken> findByTokenAndExpiresAtAfter(String token, Instant now);

    void deleteByUser_Id(Long userId);

    void deleteByExpiresAtBefore(Instant instant);
}
