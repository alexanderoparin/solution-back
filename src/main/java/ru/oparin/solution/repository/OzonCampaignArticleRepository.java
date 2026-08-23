package ru.oparin.solution.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ru.oparin.solution.model.OzonCampaignArticle;
import ru.oparin.solution.model.OzonCampaignArticleId;

import java.util.Collection;
import java.util.List;

/**
 * Репозиторий связей РК Ozon ↔ SKU.
 */
@Repository
public interface OzonCampaignArticleRepository extends JpaRepository<OzonCampaignArticle, OzonCampaignArticleId> {

    List<OzonCampaignArticle> findBySku(Long sku);

    @Query("SELECT a FROM OzonCampaignArticle a JOIN FETCH a.campaign c JOIN FETCH c.cabinet WHERE a.productId = :productId")
    List<OzonCampaignArticle> findByProductId(@Param("productId") Long productId);

    @Query("SELECT a FROM OzonCampaignArticle a JOIN FETCH a.campaign c JOIN FETCH c.cabinet WHERE a.sku = :sku")
    List<OzonCampaignArticle> findBySkuFetched(@Param("sku") Long sku);

    List<OzonCampaignArticle> findByCampaignIdIn(Collection<Long> campaignIds);

    @Query("SELECT a.campaignId, COUNT(a) FROM OzonCampaignArticle a WHERE a.campaignId IN :campaignIds GROUP BY a.campaignId")
    List<Object[]> countByCampaignIdIn(@Param("campaignIds") Collection<Long> campaignIds);

    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM OzonCampaignArticle a WHERE a.campaignId = :campaignId")
    void deleteByCampaignId(@Param("campaignId") Long campaignId);

    @Query("SELECT a.campaignId FROM OzonCampaignArticle a WHERE a.campaign.cabinet.id = :cabinetId")
    List<Long> findCampaignIdByCabinetId(@Param("cabinetId") Long cabinetId, Pageable pageable);

    @Query("""
            SELECT DISTINCT a.productId FROM OzonCampaignArticle a
            WHERE a.campaign.cabinet.id = :cabinetId
              AND a.productId IS NOT NULL
              AND a.campaign.state <> 'CAMPAIGN_STATE_FINISHED'
            """)
    List<Long> findActiveProductIdsByCabinetId(@Param("cabinetId") Long cabinetId);
}
