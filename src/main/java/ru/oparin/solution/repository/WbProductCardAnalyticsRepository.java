package ru.oparin.solution.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ru.oparin.solution.model.WbProductCardAnalytics;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Репозиторий для работы с аналитикой карточек товаров.
 */
@Repository
public interface WbProductCardAnalyticsRepository extends JpaRepository<WbProductCardAnalytics, Long> {

    /**
     * Находит аналитику по nmID и дате.
     */
    Optional<WbProductCardAnalytics> findByProductCardNmIdAndDate(Long nmId, LocalDate date);

    /**
     * Находит всю аналитику по nmID.
     */
    List<WbProductCardAnalytics> findByProductCardNmId(Long nmId);

    /**
     * Находит аналитику по nmID за период.
     */
    List<WbProductCardAnalytics> findByProductCardNmIdAndDateBetween(
            Long nmId, 
            LocalDate dateFrom, 
            LocalDate dateTo
    );

    /**
     * Находит аналитику по списку nmID за период.
     */
    List<WbProductCardAnalytics> findByProductCardNmIdInAndDateBetween(
            List<Long> nmIds,
            LocalDate dateFrom,
            LocalDate dateTo
    );

    Optional<WbProductCardAnalytics> findByProductCardNmIdAndDateAndCabinet_Id(Long nmId, LocalDate date, Long cabinetId);

    List<WbProductCardAnalytics> findByCabinet_IdAndProductCardNmIdAndDateBetween(
            Long cabinetId, Long nmId, LocalDate dateFrom, LocalDate dateTo
    );

    List<WbProductCardAnalytics> findByCabinet_IdAndProductCardNmIdInAndDateBetween(
            Long cabinetId,
            List<Long> nmIds,
            LocalDate dateFrom,
            LocalDate dateTo
    );

    void deleteByCabinet_Id(Long cabinetId);

    /**
     * Выборка только ID по кабинету пачкой (для пакетного удаления по ключам).
     */
    @Query("SELECT a.id FROM WbProductCardAnalytics a WHERE a.cabinet.id = :cabinetId")
    List<Long> findIdByCabinet_Id(@Param("cabinetId") Long cabinetId, Pageable pageable);
}

