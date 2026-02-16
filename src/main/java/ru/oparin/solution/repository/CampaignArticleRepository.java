package ru.oparin.solution.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ru.oparin.solution.model.CampaignArticle;
import ru.oparin.solution.model.CampaignArticleId;

import java.util.List;

/**
 * Репозиторий для работы со связями рекламных кампаний и артикулов.
 */
@Repository
public interface CampaignArticleRepository extends JpaRepository<CampaignArticle, CampaignArticleId> {

    /**
     * Поиск всех артикулов для кампании.
     *
     * @param campaignId ID кампании
     * @return список связей кампания-артикул
     */
    List<CampaignArticle> findByCampaignId(Long campaignId);

    /**
     * Поиск всех кампаний для артикула.
     *
     * @param nmId артикул товара
     * @return список связей кампания-артикул
     */
    List<CampaignArticle> findByNmId(Long nmId);

    /**
     * Удаление всех связей для кампании.
     *
     * @param campaignId ID кампании
     */
    void deleteByCampaignId(Long campaignId);

    /**
     * Удаление всех связей для списка кампаний разом.
     */
    void deleteByCampaignIdIn(List<Long> campaignIds);

    /**
     * Выборка только ключей (campaignId, nmId) по кабинету пачкой (для пакетного удаления по ключам).
     */
    @Query("SELECT new ru.oparin.solution.model.CampaignArticleId(c.campaignId, c.nmId) FROM CampaignArticle c WHERE c.campaign.cabinet.id = :cabinetId")
    List<CampaignArticleId> findIdByCampaign_Cabinet_Id(@Param("cabinetId") Long cabinetId, Pageable pageable);
}

