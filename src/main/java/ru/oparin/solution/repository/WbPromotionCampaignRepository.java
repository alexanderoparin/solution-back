package ru.oparin.solution.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ru.oparin.solution.model.WbPromotionCampaign;

import java.util.List;
import java.util.Optional;

/**
 * Репозиторий для работы с рекламными кампаниями.
 */
@Repository
public interface WbPromotionCampaignRepository extends JpaRepository<WbPromotionCampaign, Long> {

    /**
     * Все кампании продавца (по всем его кабинетам).
     */
    List<WbPromotionCampaign> findByCabinet_User_Id(Long userId);

    /**
     * Поиск всех кампаний кабинета.
     */
    List<WbPromotionCampaign> findByCabinet_Id(Long cabinetId);

    /**
     * Выборка только ID кампаний (advertId) по кабинету пачкой (для пакетного удаления по ключам).
     */
    @Query("SELECT p.advertId FROM WbPromotionCampaign p WHERE p.cabinet.id = :cabinetId")
    List<Long> findAdvertIdByCabinet_Id(@Param("cabinetId") Long cabinetId, Pageable pageable);

    /**
     * Поиск кампании по advertId и кабинету.
     */
    Optional<WbPromotionCampaign> findByAdvertIdAndCabinet_Id(Long advertId, Long cabinetId);

    void deleteByCabinet_Id(Long cabinetId);
}

