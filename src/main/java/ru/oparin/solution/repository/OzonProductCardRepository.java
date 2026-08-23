package ru.oparin.solution.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.oparin.solution.model.OzonProductCard;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface OzonProductCardRepository extends JpaRepository<OzonProductCard, Long> {

    List<OzonProductCard> findByCabinet_IdOrderByProductIdAsc(Long cabinetId);

    Optional<OzonProductCard> findByCabinet_IdAndProductId(Long cabinetId, Long productId);

    List<OzonProductCard> findByCabinet_IdAndSkuIn(Long cabinetId, Collection<Long> skus);

    @Query("SELECT c.id FROM OzonProductCard c WHERE c.cabinet.id = :cabinetId")
    List<Long> findIdByCabinet_Id(@Param("cabinetId") Long cabinetId, Pageable pageable);
}
