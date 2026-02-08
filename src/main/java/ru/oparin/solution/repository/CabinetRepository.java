package ru.oparin.solution.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.oparin.solution.model.Cabinet;
import ru.oparin.solution.model.Role;

import java.util.List;
import java.util.Optional;

/**
 * Репозиторий для работы с кабинетами продавцов.
 */
@Repository
public interface CabinetRepository extends JpaRepository<Cabinet, Long> {

    /**
     * Все кабинеты продавца, отсортированные по дате создания (новые первые).
     * Кабинет по умолчанию — первый в списке (последний добавленный).
     */
    List<Cabinet> findByUser_IdOrderByCreatedAtDesc(Long userId);

    /**
     * Проверка, что кабинет принадлежит пользователю (для контроля доступа).
     */
    boolean existsByIdAndUser_Id(Long id, Long userId);

    /**
     * Кабинет по умолчанию для пользователя (последний созданный).
     */
    default Optional<Cabinet> findDefaultByUserId(Long userId) {
        return findByUser_IdOrderByCreatedAtDesc(userId).stream().findFirst();
    }

    /**
     * Все кабинеты с заданным API-ключом и активным продавцом (для планировщика загрузки данных).
     */
    List<Cabinet> findByApiKeyIsNotNullAndUser_IsActiveTrueAndUser_RoleOrderByIdAsc(Role role);
}
