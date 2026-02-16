package ru.oparin.solution.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.oparin.solution.model.ProductCard;

import java.util.List;
import java.util.Optional;

/**
 * Репозиторий для работы с карточками товаров.
 */
@Repository
public interface ProductCardRepository extends JpaRepository<ProductCard, Long> {

    /**
     * Находит карточку по nmID.
     */
    Optional<ProductCard> findByNmId(Long nmId);

    /**
     * Находит все карточки продавца.
     */
    List<ProductCard> findBySellerId(Long sellerId);

    /**
     * Находит все карточки кабинета.
     */
    List<ProductCard> findByCabinet_Id(Long cabinetId);

    /**
     * Находит карточку по nmID и кабинету.
     */
    Optional<ProductCard> findByNmIdAndCabinet_Id(Long nmId, Long cabinetId);

    /**
     * Находит все карточки с заданным IMT ID в кабинете (товары «в связке»).
     */
    List<ProductCard> findByImtIdAndCabinet_Id(Long imtId, Long cabinetId);

    /**
     * Находит все карточки с заданным IMT ID у продавца (товары «в связке»).
     */
    List<ProductCard> findByImtIdAndSeller_Id(Long imtId, Long sellerId);

    void deleteByCabinet_Id(Long cabinetId);
}

