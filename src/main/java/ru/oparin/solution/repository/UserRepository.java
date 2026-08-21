package ru.oparin.solution.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import ru.oparin.solution.model.Role;
import ru.oparin.solution.model.User;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Репозиторий для работы с пользователями.
 */
@Repository
public interface UserRepository extends JpaRepository<User, Long>, UserManagementCriteriaRepository {
    /**
     * Поиск пользователя по email.
     *
     * @param email email пользователя
     * @return пользователь или пусто
     */
    Optional<User> findByEmail(String email);

    /**
     * Проверка существования пользователя с указанным email.
     *
     * @param email email пользователя
     * @return true если пользователь существует
     */
    boolean existsByEmail(String email);

    /**
     * Поиск активных пользователей по роли.
     *
     * @param role роль пользователя
     * @param isActive флаг активности
     * @return список активных пользователей с указанной ролью
     */
    List<User> findByRoleAndIsActive(Role role, Boolean isActive);

    /**
     * Поиск пользователей по роли.
     *
     * @param role роль пользователя
     * @return список пользователей с указанной ролью
     */
    List<User> findByRole(Role role);

    /**
     * Обновляет время последней активности не чаще заданного порога.
     *
     * @return число обновлённых строк (0/1)
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("""
            update User u
            set u.lastSeenAt = :seenAt
            where u.id = :userId
              and (u.lastSeenAt is null or u.lastSeenAt < :minAllowedPreviousSeenAt)
            """)
    int touchLastSeenAtIfOlderThan(
            @Param("userId") Long userId,
            @Param("seenAt") LocalDateTime seenAt,
            @Param("minAllowedPreviousSeenAt") LocalDateTime minAllowedPreviousSeenAt
    );
}


