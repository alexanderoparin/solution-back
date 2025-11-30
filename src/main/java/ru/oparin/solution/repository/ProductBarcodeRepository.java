package ru.oparin.solution.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.oparin.solution.model.ProductBarcode;

import java.util.List;
import java.util.Optional;

/**
 * Репозиторий для работы с баркодами товаров.
 */
@Repository
public interface ProductBarcodeRepository extends JpaRepository<ProductBarcode, Long> {

    /**
     * Находит баркод по nmID и sku.
     */
    Optional<ProductBarcode> findByNmIdAndSku(Long nmId, String sku);

    /**
     * Находит все баркоды для товара.
     */
    List<ProductBarcode> findByNmId(Long nmId);

    /**
     * Находит все баркоды для списка товаров.
     */
    List<ProductBarcode> findByNmIdIn(List<Long> nmIds);

    /**
     * Удаляет все баркоды для товара.
     */
    void deleteByNmId(Long nmId);
}

