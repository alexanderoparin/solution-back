package ru.oparin.solution.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.oparin.solution.model.OzonProductCard;

import java.util.List;
import java.util.Optional;

public interface OzonProductCardRepository extends JpaRepository<OzonProductCard, Long> {

    List<OzonProductCard> findByCabinet_IdOrderByProductIdAsc(Long cabinetId);

    Optional<OzonProductCard> findByCabinet_IdAndProductId(Long cabinetId, Long productId);
}
