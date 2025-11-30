package ru.oparin.solution.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.oparin.solution.model.ProductStock;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Репозиторий для работы с остатками товаров.
 */
@Repository
public interface ProductStockRepository extends JpaRepository<ProductStock, Long> {

    /**
     * Находит остаток по nmID, warehouseId, sku и дате.
     */
    Optional<ProductStock> findByNmIdAndWarehouseIdAndSkuAndDate(
            Long nmId, Long warehouseId, String sku, LocalDate date
    );

    /**
     * Находит все остатки для товара на складе за указанную дату.
     */
    List<ProductStock> findByNmIdAndWarehouseIdAndDate(Long nmId, Long warehouseId, LocalDate date);

    /**
     * Находит все остатки для товара за указанную дату (на всех складах).
     */
    List<ProductStock> findByNmIdAndDate(Long nmId, LocalDate date);

    /**
     * Находит все остатки для списка товаров за указанную дату.
     */
    List<ProductStock> findByNmIdInAndDate(List<Long> nmIds, LocalDate date);

    /**
     * Находит все остатки на складе за указанную дату.
     */
    List<ProductStock> findByWarehouseIdAndDate(Long warehouseId, LocalDate date);

    /**
     * Удаляет все остатки для товара на складе за указанную дату.
     */
    void deleteByNmIdAndWarehouseIdAndDate(Long nmId, Long warehouseId, LocalDate date);
}

