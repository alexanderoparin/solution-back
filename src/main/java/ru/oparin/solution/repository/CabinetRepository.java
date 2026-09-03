package ru.oparin.solution.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ru.oparin.solution.model.Cabinet;
import ru.oparin.solution.model.Role;

import java.time.LocalDateTime;
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

    boolean existsByUser_Id(Long userId);

    /**
     * Проверка, что кабинет принадлежит пользователю (для контроля доступа).
     */
    boolean existsByIdAndUser_Id(Long id, Long userId);

    /**
     * Кабинет по умолчанию для пользователя (последний созданный среди активных).
     */
    default Optional<Cabinet> findDefaultByUserId(Long userId) {
        return findByUser_IdOrderByCreatedAtDesc(userId).stream()
                .filter(c -> c.getDeletionStartedAt() == null)
                .findFirst();
    }

    /**
     * Атомарно помечает кабинет как удаляемый. 0 — уже в очереди на удаление.
     */
    @Modifying(clearAutomatically = true)
    @Query("""
            UPDATE Cabinet c
            SET c.deletionStartedAt = :now
            WHERE c.id = :id AND c.deletionStartedAt IS NULL
            """)
    int markDeletionStartedIfUnset(@Param("id") Long id, @Param("now") LocalDateTime now);

    /**
     * Кабинет по ID с загруженным User (для асинхронных методов, где нет сессии).
     */
    @Query("SELECT c FROM Cabinet c JOIN FETCH c.user WHERE c.id = :id")
    Optional<Cabinet> findByIdWithUser(@Param("id") Long id);

    /**
     * WB-кабинеты с API-ключом и активным продавцом (для планировщика WB sync).
     */
    @Query("""
            SELECT c FROM Cabinet c JOIN FETCH c.user u
            WHERE EXISTS (
                SELECT 1 FROM CabinetIntegration i
                WHERE i.cabinetId = c.id
                  AND i.integrationType = ru.oparin.solution.model.CabinetIntegrationType.WB_API
                  AND i.credentialPrimary IS NOT NULL AND i.credentialPrimary <> ''
            )
              AND u.isActive = true AND u.role = :role
              AND c.marketplaceType = ru.oparin.solution.model.MarketplaceType.WB
              AND c.deletionStartedAt IS NULL
            ORDER BY c.id
            """)
    List<Cabinet> findCabinetsWithApiKeyAndUser(@Param("role") Role role);

    /**
     * Ozon-кабинеты с Client-Id + Api-Key и активным продавцом.
     */
    @Query("""
            SELECT c FROM Cabinet c JOIN FETCH c.user u
            WHERE EXISTS (
                SELECT 1 FROM CabinetIntegration i
                WHERE i.cabinetId = c.id
                  AND i.integrationType = ru.oparin.solution.model.CabinetIntegrationType.OZON_SELLER
                  AND i.credentialPrimary IS NOT NULL AND i.credentialPrimary <> ''
                  AND i.credentialSecondary IS NOT NULL AND i.credentialSecondary <> ''
            )
              AND u.isActive = true AND u.role = :role
              AND c.marketplaceType = ru.oparin.solution.model.MarketplaceType.OZON
              AND c.deletionStartedAt IS NULL
            ORDER BY c.id
            """)
    List<Cabinet> findOzonCabinetsWithApiKeyAndUser(@Param("role") Role role);
}
