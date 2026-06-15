package ru.oparin.solution.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ru.oparin.solution.model.Cabinet;
import ru.oparin.solution.model.Role;

import java.util.List;
import java.util.Optional;

/**
 * Репозиторий для работы с кабинетами продавцов.
 */
@Repository
public interface CabinetRepository extends JpaRepository<Cabinet, Long>, JpaSpecificationExecutor<Cabinet> {

    /**
     * Все кабинеты продавца, отсортированные по дате создания (новые первые).
     * Кабинет по умолчанию — первый в списке (последний добавленный).
     */
    List<Cabinet> findByUser_IdOrderByCreatedAtDesc(Long userId);

    /**
     * Последний кабинет с заданным API-ключом (для определения типа токена по ключу).
     */
    Optional<Cabinet> findTopByApiKeyOrderByIdDesc(String apiKey);

    /**
     * Проверка, что API-ключ уже привязан к другому кабинету.
     */
    boolean existsByApiKeyAndIdNot(String apiKey, Long id);

    boolean existsByApiKey(String apiKey);

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
     * Кабинет по ID с загруженным User (для асинхронных методов, где нет сессии).
     */
    @Query("SELECT c FROM Cabinet c JOIN FETCH c.user WHERE c.id = :id")
    Optional<Cabinet> findByIdWithUser(@Param("id") Long id);

    /**
     * Все кабинеты с заданным API-ключом и активным продавцом (для планировщика загрузки данных).
     * Загружает User (join fetch), чтобы обращение к cabinet.getUser() не требовало сессии в другом потоке.
     */
    @Query("SELECT c FROM Cabinet c JOIN FETCH c.user u WHERE c.apiKey IS NOT NULL AND u.isActive = true AND u.role = :role ORDER BY c.id")
    List<Cabinet> findCabinetsWithApiKeyAndUser(@Param("role") Role role);

    /**
     * Кабинеты с API-ключом у активных SELLER, доступных менеджеру через grant.
     */
    @Query("""
            SELECT c
            FROM Cabinet c
            JOIN FETCH c.user u
            WHERE c.apiKey IS NOT NULL
              AND u.isActive = true
              AND u.role = :role
              AND EXISTS (
                  SELECT 1 FROM SellerManagerAccess a
                  WHERE a.seller.id = u.id
                    AND a.manager.id = :managerId
                    AND a.status = ru.oparin.solution.model.SellerManagerAccessStatus.ACTIVE
              )
            ORDER BY c.id
            """)
    List<Cabinet> findCabinetsWithApiKeyForManager(
            @Param("role") Role role,
            @Param("managerId") Long managerId
    );
}
