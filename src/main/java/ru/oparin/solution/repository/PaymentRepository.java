package ru.oparin.solution.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.oparin.solution.model.Payment;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {

    List<Payment> findByUser_IdOrderByCreatedAtDesc(Long userId);

    List<Payment> findByStatusAndCreatedAtBefore(String status, LocalDateTime createdAtBefore);

    List<Payment> findByStatusAndExternalIdIsNotNullAndCreatedAtBefore(
            String status,
            LocalDateTime createdAtBefore
    );

    Optional<Payment> findByExternalId(String externalId);
}

