package ru.oparin.solution.repository;

import org.springframework.data.jpa.repository.JpaRepository;
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
}

