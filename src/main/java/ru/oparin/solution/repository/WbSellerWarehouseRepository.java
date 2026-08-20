package ru.oparin.solution.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ru.oparin.solution.model.WbSellerWarehouse;

import java.util.List;
import java.util.Optional;

/**
 * Репозиторий складов продавца (FBS/Marketplace).
 */
@Repository
public interface WbSellerWarehouseRepository extends JpaRepository<WbSellerWarehouse, Long> {

    /**
     * Все склады продавца кабинета.
     */
    List<WbSellerWarehouse> findByCabinet_Id(Long cabinetId);

    /**
     * Склад продавца кабинета по WB warehouseId.
     */
    Optional<WbSellerWarehouse> findByCabinet_IdAndWarehouseId(Long cabinetId, Long warehouseId);

    /**
     * Выборка внутренних ID по кабинету пачкой (удаление кабинета).
     */
    @Query("SELECT w.id FROM WbSellerWarehouse w WHERE w.cabinet.id = :cabinetId")
    List<Long> findIdByCabinet_Id(@Param("cabinetId") Long cabinetId, Pageable pageable);
}
