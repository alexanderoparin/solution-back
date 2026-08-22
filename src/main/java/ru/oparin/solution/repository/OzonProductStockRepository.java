package ru.oparin.solution.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.oparin.solution.model.OzonProductStock;

import java.util.List;
import java.util.Optional;

public interface OzonProductStockRepository extends JpaRepository<OzonProductStock, Long> {

    List<OzonProductStock> findByCabinet_IdAndProductId(Long cabinetId, Long productId);

    Optional<OzonProductStock> findByCabinet_IdAndProductIdAndSkuAndStockType(
            Long cabinetId,
            Long productId,
            Long sku,
            String stockType
    );

    @Query("SELECT e.id FROM OzonProductStock e WHERE e.cabinet.id = :cabinetId")
    List<Long> findIdByCabinet_Id(@Param("cabinetId") Long cabinetId, Pageable pageable);
}
