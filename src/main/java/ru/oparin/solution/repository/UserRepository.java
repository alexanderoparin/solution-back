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
}

