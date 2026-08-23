package ru.oparin.solution.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.oparin.solution.model.OzonProductCardAnalytics;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface OzonProductCardAnalyticsRepository extends JpaRepository<OzonProductCardAnalytics, Long> {

    Optional<OzonProductCardAnalytics> findByCabinet_IdAndProductIdAndDate(
            Long cabinetId,
            Long productId,
            LocalDate date
    );

    List<OzonProductCardAnalytics> findByCabinet_IdAndProductIdAndDateBetween(
            Long cabinetId,
            Long productId,
            LocalDate dateFrom,
            LocalDate dateTo
    );

    List<OzonProductCardAnalytics> findByCabinet_IdAndDateBetween(
            Long cabinetId,
            LocalDate dateFrom,
            LocalDate dateTo
    );

    @Query("SELECT e.id FROM OzonProductCardAnalytics e WHERE e.cabinet.id = :cabinetId")
    List<Long> findIdByCabinet_Id(@Param("cabinetId") Long cabinetId, Pageable pageable);
}
