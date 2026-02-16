package ru.oparin.solution.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ru.oparin.solution.model.CampaignStatus;
import ru.oparin.solution.model.CampaignType;
import ru.oparin.solution.model.PromotionCampaign;

import java.util.List;
import java.util.Optional;

/**
 * Репозиторий для работы с рекламными кампаниями.
 */
@Repository
public interface PromotionCampaignRepository extends JpaRepository<PromotionCampaign, Long> {

    /**
     * Поиск кампании по ID и продавцу.
     *
     * @param advertId ID кампании
     * @param sellerId ID продавца
     * @return кампания или пусто
     */
    Optional<PromotionCampaign> findByAdvertIdAndSellerId(Long advertId, Long sellerId);

    /**
     * Поиск всех кампаний продавца.
     *
     * @param sellerId ID продавца
     * @return список кампаний
     */
    List<PromotionCampaign> findBySellerId(Long sellerId);

    /**
     * Поиск кампаний продавца по статусу.
     *
     * @param sellerId ID продавца
     * @param status статус кампании
     * @return список кампаний
     */
    List<PromotionCampaign> findBySellerIdAndStatus(Long sellerId, CampaignStatus status);

    /**
     * Поиск кампаний продавца по типу.
     *
     * @param sellerId ID продавца
     * @param type тип кампании
     * @return список кампаний
     */
    List<PromotionCampaign> findBySellerIdAndType(Long sellerId, CampaignType type);

    /**
     * Поиск всех кампаний кабинета.
     */
    List<PromotionCampaign> findByCabinet_Id(Long cabinetId);

    /**
     * Выборка только ID кампаний (advertId) по кабинету пачкой (для пакетного удаления по ключам).
     */
    @Query("SELECT p.advertId FROM PromotionCampaign p WHERE p.cabinet.id = :cabinetId")
    List<Long> findAdvertIdByCabinet_Id(@Param("cabinetId") Long cabinetId, Pageable pageable);

    /**
     * Поиск кампании по advertId и кабинету.
     */
    Optional<PromotionCampaign> findByAdvertIdAndCabinet_Id(Long advertId, Long cabinetId);

    void deleteByCabinet_Id(Long cabinetId);
}

