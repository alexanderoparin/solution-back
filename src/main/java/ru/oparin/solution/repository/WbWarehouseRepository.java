package ru.oparin.solution.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.oparin.solution.model.WbWarehouse;

import java.util.List;
import java.util.Optional;

/**
 * Репозиторий для работы со складами WB.
 */
@Repository
public interface WbWarehouseRepository extends JpaRepository<WbWarehouse, Integer> {

    /**
     * Находит склад по ID.
     */
    Optional<WbWarehouse> findById(Integer id);

    /**
     * Находит все склады, отсортированные по ID.
     */
    List<WbWarehouse> findAllByOrderByIdAsc();
}

