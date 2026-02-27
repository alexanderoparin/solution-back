package ru.oparin.solution.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.oparin.solution.model.Payment;

import java.util.List;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {

    List<Payment> findByUser_IdOrderByCreatedAtDesc(Long userId);
}

