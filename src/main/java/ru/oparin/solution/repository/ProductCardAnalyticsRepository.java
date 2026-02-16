package ru.oparin.solution.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ru.oparin.solution.model.ProductCardAnalytics;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Репозиторий для работы с аналитикой карточек товаров.
 */
@Repository
public interface ProductCardAnalyticsRepository extends JpaRepository<ProductCardAnalytics, Long> {

    /**
     * Находит аналитику по nmID и дате.
     */
    Optional<ProductCardAnalytics> findByProductCardNmIdAndDate(Long nmId, LocalDate date);

    /**
     * Находит всю аналитику по nmID.
     */
    List<ProductCardAnalytics> findByProductCardNmId(Long nmId);

    /**
     * Находит аналитику по nmID за период.
     */
    List<ProductCardAnalytics> findByProductCardNmIdAndDateBetween(
            Long nmId, 
            LocalDate dateFrom, 
            LocalDate dateTo
    );

    /**
     * Находит аналитику по списку nmID за период.
     */
    List<ProductCardAnalytics> findByProductCardNmIdInAndDateBetween(
            List<Long> nmIds,
            LocalDate dateFrom,
            LocalDate dateTo
    );

    Optional<ProductCardAnalytics> findByProductCardNmIdAndDateAndCabinet_Id(Long nmId, LocalDate date, Long cabinetId);

    List<ProductCardAnalytics> findByCabinet_IdAndProductCardNmIdAndDateBetween(
            Long cabinetId, Long nmId, LocalDate dateFrom, LocalDate dateTo
    );

    void deleteByCabinet_Id(Long cabinetId);

    /**
     * Выборка только ID по кабинету пачкой (для пакетного удаления по ключам).
     */
    @Query("SELECT a.id FROM ProductCardAnalytics a WHERE a.cabinet.id = :cabinetId")
    List<Long> findIdByCabinet_Id(@Param("cabinetId") Long cabinetId, Pageable pageable);
}

