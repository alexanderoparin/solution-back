package ru.oparin.solution.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.oparin.solution.model.User;

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
}

