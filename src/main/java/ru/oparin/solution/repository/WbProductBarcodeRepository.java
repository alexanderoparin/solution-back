package ru.oparin.solution.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ru.oparin.solution.model.WbProductBarcode;

import java.util.List;
import java.util.Optional;

/**
 * Репозиторий для работы с баркодами товаров.
 */
@Repository
public interface WbProductBarcodeRepository extends JpaRepository<WbProductBarcode, String> {

    Optional<WbProductBarcode> findByBarcode(String barcode);

    Optional<WbProductBarcode> findByBarcodeAndCabinet_Id(String barcode, Long cabinetId);

    Optional<WbProductBarcode> findByNmIdAndBarcode(Long nmId, String barcode);

    List<WbProductBarcode> findByNmIdAndCabinet_Id(Long nmId, Long cabinetId);

    /**
     * Все баркоды кабинета.
     */
    List<WbProductBarcode> findByCabinet_Id(Long cabinetId);

    /**
     * Находит все баркоды для товара.
     */
    List<WbProductBarcode> findByNmId(Long nmId);

    /**
     * Находит все баркоды для списка товаров.
     */
    List<WbProductBarcode> findByNmIdIn(List<Long> nmIds);

    /**
     * Удаляет все баркоды для товара.
     */
    void deleteByNmId(Long nmId);

    void deleteByCabinet_Id(Long cabinetId);

    /**
     * Выборка только ключей (barcode) по кабинету пачкой (для пакетного удаления по ключам).
     */
    @Query("SELECT b.barcode FROM WbProductBarcode b WHERE b.cabinet.id = :cabinetId")
    List<String> findBarcodeByCabinet_Id(@Param("cabinetId") Long cabinetId, Pageable pageable);
}

