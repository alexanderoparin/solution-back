package ru.oparin.solution.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ru.oparin.solution.model.ProductStock;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Репозиторий для работы с остатками товаров.
 */
@Repository
public interface ProductStockRepository extends JpaRepository<ProductStock, Long> {

    /**
     * Находит остаток по nmID, warehouseId и баркоду.
     */
    Optional<ProductStock> findByNmIdAndWarehouseIdAndBarcode(
            Long nmId, Long warehouseId, String barcode
    );

    Optional<ProductStock> findByNmIdAndWarehouseIdAndBarcodeAndCabinetId(
            Long nmId, Long warehouseId, String barcode, Long cabinetId
    );

    List<ProductStock> findByCabinet_Id(Long cabinetId);

    List<ProductStock> findByNmIdAndCabinet_Id(Long nmId, Long cabinetId);

    void deleteByCabinet_IdAndNmId(Long cabinetId, Long nmId);

    /**
     * Находит все остатки для товара на складе.
     */
    List<ProductStock> findByNmIdAndWarehouseId(Long nmId, Long warehouseId);

    /**
     * Находит все остатки для товара (на всех складах).
     */
    List<ProductStock> findByNmId(Long nmId);

    /**
     * Находит все остатки для списка товаров.
     */
    List<ProductStock> findByNmIdIn(List<Long> nmIds);

    /**
     * Находит все остатки на складе.
     */
    List<ProductStock> findByWarehouseId(Long warehouseId);

    /**
     * Удаляет все остатки для товара на складе.
     */
    void deleteByNmIdAndWarehouseId(Long nmId, Long warehouseId);

    /**
     * Удаляет все остатки для товара.
     */
    void deleteByNmId(Long nmId);

    void deleteByCabinet_Id(Long cabinetId);

    /**
     * Выборка только ID по кабинету пачкой (для пакетного удаления по ключам).
     */
    @Query("SELECT s.id FROM ProductStock s WHERE s.cabinet.id = :cabinetId")
    List<Long> findIdByCabinet_Id(@Param("cabinetId") Long cabinetId, Pageable pageable);

    /**
     * Минимальная дата обновления остатков по кабинету (самая старая среди всех остатков).
     * Используется для ограничения частоты ручного обновления: кулдаун считается по самой старой записи.
     */
    @Query("SELECT MIN(s.updatedAt) FROM ProductStock s WHERE s.cabinet.id = :cabinetId")
    Optional<LocalDateTime> findMinUpdatedAtByCabinet_Id(@Param("cabinetId") Long cabinetId);
}

