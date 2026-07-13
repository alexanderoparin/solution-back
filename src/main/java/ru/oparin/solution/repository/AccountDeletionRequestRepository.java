package ru.oparin.solution.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.oparin.solution.model.AccountDeletionRequest;
import ru.oparin.solution.model.AccountDeletionRequestStatus;

import java.util.List;
import java.util.Optional;

public interface AccountDeletionRequestRepository extends JpaRepository<AccountDeletionRequest, Long> {

    Optional<AccountDeletionRequest> findByUser_IdAndStatus(Long userId, AccountDeletionRequestStatus status);

    List<AccountDeletionRequest> findByStatusOrderByCreatedAtDesc(AccountDeletionRequestStatus status);

    List<AccountDeletionRequest> findAllByOrderByCreatedAtDesc();
}
