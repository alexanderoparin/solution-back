package ru.oparin.solution.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ru.oparin.solution.model.SellerManagerAccess;
import ru.oparin.solution.model.SellerManagerAccessStatus;

import java.util.List;

/**
 * Репозиторий доступов менеджеров к селлерам.
 */
@Repository
public interface SellerManagerAccessRepository extends JpaRepository<SellerManagerAccess, Long> {

    /**
     * Возвращает активные email менеджеров по списку селлеров.
     */
    @Query("""
            select a.seller.id as sellerId, a.manager.email as managerEmail
            from SellerManagerAccess a
            where a.status = :status
              and a.seller.id in :sellerIds
            order by a.seller.id, a.manager.email
            """)
    List<SellerManagerAccessEmailProjection> findManagerEmailsBySellerIdsAndStatus(
            @Param("sellerIds") List<Long> sellerIds,
            @Param("status") SellerManagerAccessStatus status
    );
}
