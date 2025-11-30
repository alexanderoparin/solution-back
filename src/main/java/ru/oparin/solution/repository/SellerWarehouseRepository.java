package ru.oparin.solution.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.oparin.solution.model.SellerWarehouse;

import java.util.List;
import java.util.Optional;

/**
 * Репозиторий для работы со складами продавца.
 */
@Repository
public interface SellerWarehouseRepository extends JpaRepository<SellerWarehouse, Long> {

    /**
     * Находит все склады продавца.
     */
    List<SellerWarehouse> findBySellerId(Long sellerId);

    /**
     * Находит склад по ID и ID продавца.
     */
    Optional<SellerWarehouse> findByIdAndSellerId(Long id, Long sellerId);

    /**
     * Проверяет существование склада по ID и ID продавца.
     */
    boolean existsByIdAndSellerId(Long id, Long sellerId);
}

