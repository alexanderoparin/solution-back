package ru.oparin.solution.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.oparin.solution.model.AccountDeletionRequest;
import ru.oparin.solution.model.AccountDeletionRequestStatus;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Репозиторий заявок на удаление аккаунта.
 */
public interface AccountDeletionRequestRepository extends JpaRepository<AccountDeletionRequest, Long> {

    Optional<AccountDeletionRequest> findByUser_IdAndStatus(Long userId, AccountDeletionRequestStatus status);

    /**
     * Поиск последней созданной заявки конкретного пользователя.
     *
     * @param userId идентификатор пользователя
     * @return последняя заявка на удаление (если есть)
     */
    Optional<AccountDeletionRequest> findFirstByUser_IdOrderByCreatedAtDesc(Long userId);

    /**
     * Проверка наличия у пользователя заявок в одном из указанных статусов.
     *
     * @param userId   идентификатор пользователя
     * @param statuses коллекция проверяемых статусов
     * @return true, если есть заявка в одном из статусов
     */
    boolean existsByUser_IdAndStatusIn(Long userId, Collection<AccountDeletionRequestStatus> statuses);

    List<AccountDeletionRequest> findByStatusOrderByCreatedAtDesc(AccountDeletionRequestStatus status);

    List<AccountDeletionRequest> findAllByOrderByCreatedAtDesc();

    long countByStatus(AccountDeletionRequestStatus status);
}
