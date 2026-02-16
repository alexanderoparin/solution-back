package ru.oparin.solution.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ru.oparin.solution.model.PromotionCampaignStatistics;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Репозиторий для работы со статистикой рекламных кампаний.
 */
@Repository
public interface PromotionCampaignStatisticsRepository extends JpaRepository<PromotionCampaignStatistics, Long> {

    /**
     * Поиск статистики по кампании и дате.
     *
     * @param campaignId ID кампании
     * @param date дата статистики
     * @return статистика или пусто
     */
    Optional<PromotionCampaignStatistics> findByCampaignAdvertIdAndDate(Long campaignId, LocalDate date);

    /**
     * Поиск статистики по кампании, артикулу и дате.
     *
     * @param campaignId ID кампании
     * @param nmId артикул товара
     * @param date дата статистики
     * @return статистика или пусто
     */
    Optional<PromotionCampaignStatistics> findByCampaignAdvertIdAndNmIdAndDate(
            Long campaignId,
            Long nmId,
            LocalDate date
    );

    /**
     * Поиск статистики по кампании за период.
     *
     * @param campaignId ID кампании
     * @param dateFrom дата начала периода
     * @param dateTo дата окончания периода
     * @return список статистики
     */
    List<PromotionCampaignStatistics> findByCampaignAdvertIdAndDateBetween(
            Long campaignId,
            LocalDate dateFrom,
            LocalDate dateTo
    );

    /**
     * Поиск всех дат статистики для кампании за период.
     *
     * @param campaignId ID кампании
     * @param dateFrom дата начала периода
     * @param dateTo дата окончания периода
     * @return список дат
     */
    List<LocalDate> findDatesByCampaignAdvertIdAndDateBetween(
            Long campaignId,
            LocalDate dateFrom,
            LocalDate dateTo
    );

    /**
     * Поиск статистики для списка кампаний за период (оптимизированный запрос).
     *
     * @param campaignIds список ID кампаний
     * @param dateFrom дата начала периода
     * @param dateTo дата окончания периода
     * @return список статистики
     */
    @Query("SELECT s FROM PromotionCampaignStatistics s " +
           "WHERE s.campaign.advertId IN :campaignIds " +
           "AND s.date BETWEEN :dateFrom AND :dateTo")
    List<PromotionCampaignStatistics> findByCampaignAdvertIdInAndDateBetween(
            @Param("campaignIds") List<Long> campaignIds,
            @Param("dateFrom") LocalDate dateFrom,
            @Param("dateTo") LocalDate dateTo
    );

    /**
     * Поиск статистики по артикулу за период.
     *
     * @param nmId артикул товара
     * @param dateFrom дата начала периода
     * @param dateTo дата окончания периода
     * @return список статистики
     */
    @Query("SELECT s FROM PromotionCampaignStatistics s " +
           "WHERE s.nmId = :nmId " +
           "AND s.date BETWEEN :dateFrom AND :dateTo")
    List<PromotionCampaignStatistics> findByNmIdAndDateBetween(
            @Param("nmId") Long nmId,
            @Param("dateFrom") LocalDate dateFrom,
            @Param("dateTo") LocalDate dateTo
    );

    /**
     * Список уникальных артикулов (nm_id) по кампании.
     * Используется для восстановления связей campaign_articles, когда WB API не возвращает nmIds.
     *
     * @param campaignId ID кампании (advert_id)
     * @return список nm_id
     */
    @Query("SELECT DISTINCT s.nmId FROM PromotionCampaignStatistics s WHERE s.campaign.advertId = :campaignId")
    List<Long> findDistinctNmIdsByCampaignAdvertId(@Param("campaignId") Long campaignId);

    void deleteByCampaign_AdvertId(Long advertId);

    /**
     * Удаление всей статистики по списку кампаний (advertId).
     */
    void deleteByCampaign_AdvertIdIn(List<Long> advertIds);

    /**
     * Выборка только ID статистики по кабинету пачкой (для пакетного удаления по ключам).
     */
    @Query("SELECT s.id FROM PromotionCampaignStatistics s WHERE s.campaign.cabinet.id = :cabinetId")
    List<Long> findIdByCampaign_Cabinet_Id(@Param("cabinetId") Long cabinetId, Pageable pageable);
}

