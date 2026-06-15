package ru.oparin.solution.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.oparin.solution.model.SellerWorker;
import ru.oparin.solution.model.User;

import java.util.List;
import java.util.Optional;

/**
 * Репозиторий привязки работников к селлерам.
 */
public interface SellerWorkerRepository extends JpaRepository<SellerWorker, Long> {

    Optional<SellerWorker> findByWorker_Id(Long workerId);

    @Query("SELECT sw.seller FROM SellerWorker sw WHERE sw.worker.id = :workerId")
    Optional<User> findSellerByWorkerId(@Param("workerId") Long workerId);

    @Query("SELECT sw.worker FROM SellerWorker sw WHERE sw.seller.id = :sellerId")
    List<User> findWorkersBySellerId(@Param("sellerId") Long sellerId);

    @Query("""
            SELECT CASE WHEN COUNT(sw) > 0 THEN true ELSE false END
            FROM SellerWorker sw
            WHERE sw.worker.id = :workerId
              AND sw.seller.id = :sellerId
            """)
    boolean existsByWorkerIdAndSellerId(@Param("workerId") Long workerId, @Param("sellerId") Long sellerId);

    void deleteByWorker_Id(Long workerId);
}
