package ru.oparin.solution.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.oparin.solution.model.Subscription;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {

    Optional<Subscription> findFirstByUser_IdAndStatusInAndExpiresAtAfterOrderByExpiresAtDesc(
            Long userId,
            java.util.Collection<String> statuses,
            LocalDateTime now
    );
}

