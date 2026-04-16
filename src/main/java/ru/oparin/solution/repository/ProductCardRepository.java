package ru.oparin.solution.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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
     * Находит все карточки продавца (по всем его кабинетам).
     */
    List<ProductCard> findByCabinet_User_Id(Long userId);

    /**
     * Находит все карточки кабинета.
     */
    List<ProductCard> findByCabinet_Id(Long cabinetId);

    /**
     * Выборка только ключей (nmId) по кабинету пачкой (для пакетного удаления по ключам).
     */
    @Query("SELECT c.nmId FROM ProductCard c WHERE c.cabinet.id = :cabinetId")
    List<Long> findNmIdByCabinet_Id(@Param("cabinetId") Long cabinetId, Pageable pageable);

    /**
     * Находит карточку по nmID и кабинету.
     */
    Optional<ProductCard> findByNmIdAndCabinet_Id(Long nmId, Long cabinetId);

    @Query("SELECT c.nmId FROM ProductCard c WHERE c.cabinet.id = :cabinetId AND c.nmId IN :nmIds AND c.isPriority = true")
    List<Long> findPriorityNmIdsByCabinetAndNmIdIn(@Param("cabinetId") Long cabinetId, @Param("nmIds") List<Long> nmIds);

    /**
     * Находит все карточки с заданным IMT ID в кабинете (товары «в связке»).
     */
    List<ProductCard> findByImtIdAndCabinet_Id(Long imtId, Long cabinetId);

    void deleteByCabinet_Id(Long cabinetId);
}

