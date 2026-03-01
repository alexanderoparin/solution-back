package ru.oparin.solution.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.oparin.solution.model.EmailConfirmationToken;

import java.time.Instant;
import java.util.Optional;

/**
 * Репозиторий токенов подтверждения email.
 */
@Repository
public interface EmailConfirmationTokenRepository extends JpaRepository<EmailConfirmationToken, Long> {

    Optional<EmailConfirmationToken> findByTokenAndExpiresAtAfter(String token, Instant now);

    void deleteByUser_Id(Long userId);
}
