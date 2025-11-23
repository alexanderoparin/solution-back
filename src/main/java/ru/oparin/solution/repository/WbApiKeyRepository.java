package ru.oparin.solution.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.oparin.solution.model.WbApiKey;

import java.util.Optional;

/**
 * Репозиторий для работы с WB API ключами.
 */
@Repository
public interface WbApiKeyRepository extends JpaRepository<WbApiKey, Long> {
    /**
     * Поиск API ключа по ID пользователя.
     *
     * @param userId ID пользователя
     * @return API ключ или пусто
     */
    Optional<WbApiKey> findByUserId(Long userId);
}

