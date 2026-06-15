package ru.oparin.solution.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.oparin.solution.model.SellerManagerAccess;
import ru.oparin.solution.model.SellerManagerAccessStatus;
import ru.oparin.solution.model.User;

import java.util.List;
import java.util.Optional;

/**
 * Репозиторий делегирования доступа менеджеров к селлерам.
 */
public interface SellerManagerAccessRepository extends JpaRepository<SellerManagerAccess, Long> {

    Optional<SellerManagerAccess> findBySeller_IdAndManager_Id(Long sellerId, Long managerId);

    List<SellerManagerAccess> findBySeller_IdAndStatus(Long sellerId, SellerManagerAccessStatus status);

    @Query("""
            SELECT a.seller
            FROM SellerManagerAccess a
            WHERE a.manager.id = :managerId
              AND a.status = :status
            """)
    List<User> findActiveSellersByManagerId(
            @Param("managerId") Long managerId,
            @Param("status") SellerManagerAccessStatus status
    );

    @Query("""
            SELECT CASE WHEN COUNT(a) > 0 THEN true ELSE false END
            FROM SellerManagerAccess a
            WHERE a.manager.id = :managerId
              AND a.seller.id = :sellerId
              AND a.status = :status
            """)
    boolean existsActiveAccess(
            @Param("managerId") Long managerId,
            @Param("sellerId") Long sellerId,
            @Param("status") SellerManagerAccessStatus status
    );

    @Query("""
            SELECT CASE WHEN COUNT(a) > 0 THEN true ELSE false END
            FROM SellerManagerAccess a
            WHERE a.manager.id = :managerId
              AND a.seller.id = :sellerId
              AND a.status = :status
              AND a.seller.isActive = true
            """)
    boolean existsActiveAccessToActiveSeller(
            @Param("managerId") Long managerId,
            @Param("sellerId") Long sellerId,
            @Param("status") SellerManagerAccessStatus status
    );
}
