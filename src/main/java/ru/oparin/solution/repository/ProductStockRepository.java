package ru.oparin.solution.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.oparin.solution.model.ProductStock;

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

    Optional<ProductStock> findByNmIdAndWarehouseIdAndBarcodeAndCabinet_Id(
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
}

