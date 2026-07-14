package ru.oparin.solution.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.oparin.solution.model.Subscription;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {

    Optional<Subscription> findFirstByUser_IdAndStatusInAndExpiresAtAfterOrderByExpiresAtDesc(
            Long userId,
            java.util.Collection<String> statuses,
            LocalDateTime now
    );

    Optional<Subscription> findFirstByUser_IdAndPlan_CodeStartingWithAndStatusInAndExpiresAtAfterOrderByExpiresAtDesc(
            Long userId,
            String planCodePrefix,
            java.util.Collection<String> statuses,
            LocalDateTime now
    );

    List<Subscription> findByUser_IdOrderByExpiresAtDesc(Long userId);

    Optional<Subscription> findFirstByUser_IdAndExpiresAtBeforeOrderByExpiresAtDesc(
            Long userId,
            LocalDateTime now
    );

    boolean existsByUser_IdAndPlan_Code(Long userId, String planCode);

    Optional<Subscription> findFirstByUser_IdAndPlan_CodeStartingWithAndExpiresAtBeforeOrderByExpiresAtDesc(
            Long userId,
            String planCodePrefix,
            LocalDateTime now
    );
}

