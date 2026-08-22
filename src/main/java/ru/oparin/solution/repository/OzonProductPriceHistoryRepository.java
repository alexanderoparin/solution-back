package ru.oparin.solution.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.oparin.solution.model.OzonProductPriceHistory;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface OzonProductPriceHistoryRepository extends JpaRepository<OzonProductPriceHistory, Long> {

    Optional<OzonProductPriceHistory> findByCabinet_IdAndProductIdAndDate(Long cabinetId, Long productId, LocalDate date);

    List<OzonProductPriceHistory> findByCabinet_IdAndProductIdInAndDate(
            Long cabinetId,
            List<Long> productIds,
            LocalDate date
    );
}
