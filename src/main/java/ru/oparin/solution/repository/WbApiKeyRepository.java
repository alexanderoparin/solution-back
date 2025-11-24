package ru.oparin.solution.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.oparin.solution.model.WbApiKey;

import java.util.Optional;

/**
 * Репозиторий для работы с WB API ключами.
 * Первичный ключ - user_id (Long).
 * Можно использовать findById(userId) вместо findByUserId(userId).
 */
@Repository
public interface WbApiKeyRepository extends JpaRepository<WbApiKey, Long> {
    /**
     * Поиск API ключа по ID пользователя.
     * user_id является первичным ключом, поэтому можно использовать findById(userId).
     *
     * @param userId ID пользователя (является первичным ключом)
     * @return API ключ или пусто
     */
    Optional<WbApiKey> findByUserId(Long userId);
}

