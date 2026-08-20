package ru.oparin.solution.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ru.oparin.solution.model.WbProductPriceHistory;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Репозиторий для работы с историей цен товаров.
 */
@Repository
public interface WbProductPriceHistoryRepository extends JpaRepository<WbProductPriceHistory, Long> {

    /**
     * Находит запись цены по nmID, дате и sizeId.
     */
    Optional<WbProductPriceHistory> findByNmIdAndDateAndSizeId(Long nmId, LocalDate date, Long sizeId);

    /**
     * Находит все записи цен для товара за указанную дату.
     */
    List<WbProductPriceHistory> findByNmIdAndDate(Long nmId, LocalDate date);

    /**
     * Находит текущую цену товара (за вчерашнюю дату) без размера.
     */
    Optional<WbProductPriceHistory> findByNmIdAndDateAndSizeIdIsNull(Long nmId, LocalDate date);

    /**
     * Находит все записи цен для товара.
     */
    List<WbProductPriceHistory> findByNmIdOrderByDateDesc(Long nmId);

    /**
     * Находит все записи цен за указанную дату.
     */
    List<WbProductPriceHistory> findByDate(LocalDate date);

    /**
     * Находит все записи цен для списка товаров за указанную дату.
     */
    List<WbProductPriceHistory> findByNmIdInAndDate(List<Long> nmIds, LocalDate date);

    /**
     * Находит все записи цен для товара за период.
     */
    List<WbProductPriceHistory> findByNmIdAndDateBetween(Long nmId, LocalDate dateFrom, LocalDate dateTo);

    /**
     * Подсчитывает количество уникальных товаров с ценами за указанную дату.
     */
    long countDistinctNmIdByDate(LocalDate date);

    Optional<WbProductPriceHistory> findByNmIdAndDateAndSizeIdAndCabinet_Id(Long nmId, LocalDate date, Long sizeId, Long cabinetId);

    List<WbProductPriceHistory> findByNmIdAndDateAndCabinet_Id(Long nmId, LocalDate date, Long cabinetId);

    List<WbProductPriceHistory> findByNmIdInAndDateAndCabinet_Id(List<Long> nmIds, LocalDate date, Long cabinetId);

    List<WbProductPriceHistory> findByNmIdAndDateBetweenAndCabinet_Id(Long nmId, LocalDate dateFrom, LocalDate dateTo, Long cabinetId);

    void deleteByCabinet_Id(Long cabinetId);

    /**
     * Выборка только ID по кабинету пачкой (для пакетного удаления по ключам).
     */
    @Query("SELECT e.id FROM WbProductPriceHistory e WHERE e.cabinet.id = :cabinetId")
    List<Long> findIdByCabinet_Id(@Param("cabinetId") Long cabinetId, Pageable pageable);
}

