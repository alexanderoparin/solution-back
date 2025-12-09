package ru.oparin.solution.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.oparin.solution.model.Role;
import ru.oparin.solution.model.User;

import java.util.List;
import java.util.Optional;

/**
 * Репозиторий для работы с пользователями.
 */
@Repository
public interface UserRepository extends JpaRepository<User, Long> {
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
     * Поиск пользователей по владельцу (owner_id).
     *
     * @param ownerId ID владельца
     * @return список пользователей, принадлежащих указанному владельцу
     */
    List<User> findByOwnerId(Long ownerId);

    /**
     * Поиск пользователей по роли и владельцу.
     *
     * @param role роль пользователя
     * @param ownerId ID владельца
     * @return список пользователей с указанной ролью, принадлежащих указанному владельцу
     */
    List<User> findByRoleAndOwnerId(Role role, Long ownerId);

    /**
     * Поиск пользователей по роли.
     *
     * @param role роль пользователя
     * @return список пользователей с указанной ролью
     */
    List<User> findByRole(Role role);
}

