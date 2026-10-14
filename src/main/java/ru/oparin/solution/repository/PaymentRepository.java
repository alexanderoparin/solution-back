package ru.oparin.solution.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ru.oparin.solution.model.Payment;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Репозиторий платежей за подписку.
 */
@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {

    List<Payment> findByUser_IdOrderByCreatedAtDesc(Long userId);

    List<Payment> findByStatusAndCreatedAtBefore(String status, LocalDateTime createdAtBefore);

    List<Payment> findByStatusAndExternalIdIsNotNullAndCreatedAtBefore(
            String status,
            LocalDateTime createdAtBefore
    );

    Optional<Payment> findByExternalId(String externalId);

    /**
     * Постраничный список платежей для админки с фильтром по email и статусу.
     *
     * @param email  подстрока email (без учёта регистра); пусто — без фильтра
     * @param status точный статус в БД; пусто — без фильтра
     * @param pageable пагинация и сортировка
     * @return страница платежей с user/cabinet
     */
    @Query(
            value = """
                    SELECT p FROM Payment p
                    JOIN FETCH p.user u
                    LEFT JOIN FETCH p.cabinet c
                    WHERE (:email IS NULL OR :email = '' OR LOWER(u.email) LIKE LOWER(CONCAT('%', :email, '%')))
                      AND (:status IS NULL OR :status = '' OR p.status = :status)
                    """,
            countQuery = """
                    SELECT COUNT(p) FROM Payment p
                    JOIN p.user u
                    WHERE (:email IS NULL OR :email = '' OR LOWER(u.email) LIKE LOWER(CONCAT('%', :email, '%')))
                      AND (:status IS NULL OR :status = '' OR p.status = :status)
                    """
    )
    Page<Payment> findAllForAdmin(
            @Param("email") String email,
            @Param("status") String status,
            Pageable pageable
    );
}
