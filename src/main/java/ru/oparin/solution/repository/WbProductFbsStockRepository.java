package ru.oparin.solution.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ru.oparin.solution.model.WbProductFbsStock;

import java.util.List;
import java.util.Optional;

/**
 * Репозиторий остатков FBS на складах продавца.
 */
@Repository
public interface WbProductFbsStockRepository extends JpaRepository<WbProductFbsStock, Long> {

    /**
     * Остатки FBS артикула в кабинете.
     */
    List<WbProductFbsStock> findByNmIdAndCabinet_Id(Long nmId, Long cabinetId);

    /**
     * Остатки FBS артикула на одном складе продавца.
     */
    List<WbProductFbsStock> findByNmIdAndCabinet_IdAndWarehouseId(Long nmId, Long cabinetId, Long warehouseId);

    /**
     * Остаток размера на складе продавца.
     */
    Optional<WbProductFbsStock> findByCabinet_IdAndWarehouseIdAndChrtId(
            Long cabinetId,
            Long warehouseId,
            Long chrtId
    );

    /**
     * Все остатки FBS кабинета на одном складе продавца.
     */
    List<WbProductFbsStock> findByCabinet_IdAndWarehouseId(Long cabinetId, Long warehouseId);

    /**
     * Выборка внутренних ID по кабинету пачкой (удаление кабинета).
     */
    @Query("SELECT s.id FROM WbProductFbsStock s WHERE s.cabinet.id = :cabinetId")
    List<Long> findIdByCabinet_Id(@Param("cabinetId") Long cabinetId, Pageable pageable);
}
