package ru.oparin.solution.service.sync;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import ru.oparin.solution.dto.wb.PromotionAdvertsResponse;
import ru.oparin.solution.dto.wb.PromotionCountResponse;
import ru.oparin.solution.dto.wb.PromotionFullStatsRequest;
import ru.oparin.solution.dto.wb.PromotionFullStatsResponse;
import ru.oparin.solution.exception.WbApiUnauthorizedScopeException;
import ru.oparin.solution.model.Cabinet;
import ru.oparin.solution.model.PromotionCampaign;
import ru.oparin.solution.model.User;
import ru.oparin.solution.repository.PromotionCampaignRepository;
import ru.oparin.solution.service.PromotionCampaignService;
import ru.oparin.solution.service.PromotionCampaignStatisticsService;
import ru.oparin.solution.service.wb.AbstractWbApiClient;
import ru.oparin.solution.service.wb.WbPromotionApiClient;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Синхронизация рекламных кампаний и их статистики с WB API.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PromotionCampaignSyncService {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    @Value("${wb.promotion.statistics-batch-size:50}")
    private int statisticsBatchSize;
    @Value("${wb.promotion.campaigns-batch-size:50}")
    private int campaignsBatchSize;

    private final WbPromotionApiClient promotionApiClient;
    private final PromotionCampaignService promotionCampaignService;
    private final PromotionCampaignStatisticsService campaignStatisticsService;
    private final PromotionCampaignRepository campaignRepository;

    public PromotionCountResponse fetchPromotionCount(String apiKey) {
        return promotionApiClient.getPromotionCount(apiKey);
    }

    public List<Long> listCampaignIdsFromCount(PromotionCountResponse countResponse) {
        CampaignIdsByType campaignsByType = separateCampaignsByType(countResponse);
        List<Long> all = new ArrayList<>();
        all.addAll(campaignsByType.type8Ids());
        all.addAll(campaignsByType.type9Ids());
        return all;
    }

    /**
     * Одна страница GET /advert/v2/adverts и сохранение в БД (один HTTP-запрос).
     */
    public void loadAndSaveAdvertsBatch(Cabinet cabinet, String apiKey, List<Long> batchIds) {
        PromotionAdvertsResponse batchResponse = promotionApiClient.getAdvertsV2(apiKey, batchIds);
        if (batchResponse != null && batchResponse.getAdverts() != null && !batchResponse.getAdverts().isEmpty()) {
            promotionCampaignService.saveOrUpdateCampaigns(
                    PromotionAdvertsResponse.builder().adverts(batchResponse.getAdverts()).build(),
                    cabinet
            );
        }
    }

    /**
     * Один POST fullstats и сохранение (один HTTP-запрос).
     */
    public void loadAndSaveStatisticsBatch(
            User seller,
            String apiKey,
            List<Long> batchIds,
            LocalDate dateFrom,
            LocalDate dateTo
    ) {
        String dateFromStr = dateFrom.format(DATE_FORMATTER);
        String dateToStr = dateTo.format(DATE_FORMATTER);
        PromotionFullStatsRequest request = PromotionFullStatsRequest.builder()
                .advertId(batchIds)
                .dateFrom(dateFromStr)
                .dateTo(dateToStr)
                .build();
        PromotionFullStatsResponse batchResponse = promotionApiClient.getPromotionFullStats(apiKey, request);
        if (batchResponse != null && batchResponse.getAdverts() != null && !batchResponse.getAdverts().isEmpty()) {
            campaignStatisticsService.saveOrUpdateStatistics(
                    PromotionFullStatsResponse.builder().adverts(batchResponse.getAdverts()).build(),
                    seller
            );
        }
    }

    /**
     * Все кампании кабинета (по advertId) — для них ставится в очередь загрузка статистики за период.
     * Статус кампании (в т.ч. завершена) не учитывается.
     *
     * @param dateFrom dateTo зарезервированы под контекст периода в очереди событий; отбор кампаний по ним не выполняется
     */
    @SuppressWarnings("unused")
    public List<Long> listCampaignIdsNeedingStatisticsForPeriod(Long cabinetId, LocalDate dateFrom, LocalDate dateTo) {
        return campaignRepository.findByCabinet_Id(cabinetId).stream()
                .map(PromotionCampaign::getAdvertId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    public int getCampaignsBatchSize() {
        return campaignsBatchSize;
    }

    public int getStatisticsBatchSize() {
        return statisticsBatchSize;
    }

    /**
     * Обновляет список кампаний кабинета (типы 8 и 9) и сохраняет в БД.
     *
     * @return список ID обновлённых кампаний (для последующей загрузки статистики)
     */
    public List<Long> updateCampaigns(Cabinet cabinet, String apiKey) {
        try {
            User seller = cabinet.getUser();
            log.info("Начало обновления рекламных кампаний для кабинета (ID: {}, продавец: {})",
                    cabinet.getId(), seller.getEmail());

            PromotionCountResponse countResponse = promotionApiClient.getPromotionCount(apiKey);
            CampaignIdsByType campaignsByType = separateCampaignsByType(countResponse);

            List<Long> allCampaignIds = new ArrayList<>();
            allCampaignIds.addAll(campaignsByType.type8Ids());
            allCampaignIds.addAll(campaignsByType.type9Ids());

            if (allCampaignIds.isEmpty()) {
                log.info("У кабинета (ID: {}) нет рекламных кампаний типов 8 и 9", cabinet.getId());
                return allCampaignIds;
            }

            List<PromotionAdvertsResponse.Campaign> allCampaigns = fetchAdvertsV2InBatches(apiKey, allCampaignIds);

            if (allCampaigns.isEmpty()) {
                log.info("Не удалось получить детальную информацию о кампаниях для кабинета (ID: {})", cabinet.getId());
                return allCampaignIds;
            }

            PromotionAdvertsResponse advertsResponse = PromotionAdvertsResponse.builder()
                    .adverts(allCampaigns)
                    .build();
            promotionCampaignService.saveOrUpdateCampaigns(advertsResponse, cabinet);

            log.info("Завершено обновление рекламных кампаний для кабинета (ID: {})", cabinet.getId());
            return allCampaignIds;

        } catch (WbApiUnauthorizedScopeException e) {
            throw e;
        } catch (Exception e) {
            if (AbstractWbApiClient.isConnectionIoError(e)) {
                log.warn("Ошибка при обновлении рекламных кампаний для кабинета (ID: {}): {}",
                        cabinet.getId(), e.getMessage());
            } else {
                log.error("Ошибка при обновлении рекламных кампаний для кабинета (ID: {}): {}",
                        cabinet.getId(), e.getMessage(), e);
            }
            return List.of();
        }
    }

    /**
     * Обновляет статистику кампаний за период для всех переданных advertId (в т.ч. завершённых).
     * Перезаписывает строки по (кампания, nm_id, дата).
     */
    public void updateStatistics(
            User seller,
            String apiKey,
            List<Long> campaignIds,
            LocalDate dateFrom,
            LocalDate dateTo
    ) {
        try {
            log.info("Начало обновления статистики кампаний для продавца (ID: {}, email: {}) за период {} - {}",
                    seller.getId(), seller.getEmail(), dateFrom, dateTo);

            List<Long> toFetch = campaignIds.stream()
                    .filter(Objects::nonNull)
                    .distinct()
                    .toList();

            if (toFetch.isEmpty()) {
                log.info("Нет кампаний для загрузки статистики за период {} - {}", dateFrom, dateTo);
                return;
            }

            log.info("Загрузка статистики для {} кампаний (перезапись существующих записей)", toFetch.size());

            List<PromotionFullStatsResponse.CampaignStats> allStats = fetchStatisticsInBatches(
                    apiKey, toFetch, dateFrom, dateTo);

            if (allStats.isEmpty()) {
                log.info("Не удалось получить статистику кампаний для продавца (ID: {})", seller.getId());
                return;
            }

            PromotionFullStatsResponse statsResponse = PromotionFullStatsResponse.builder()
                    .adverts(allStats)
                    .build();
            campaignStatisticsService.saveOrUpdateStatistics(statsResponse, seller);

            log.info("Завершено обновление статистики кампаний для продавца (ID: {})", seller.getId());

        } catch (Exception e) {
            if (AbstractWbApiClient.isConnectionIoError(e)) {
                log.warn("Ошибка при обновлении статистики кампаний для продавца (ID: {}, email: {}): {}",
                        seller.getId(), seller.getEmail(), e.getMessage());
            } else {
                log.error("Ошибка при обновлении статистики кампаний для продавца (ID: {}, email: {}): {}",
                        seller.getId(), seller.getEmail(), e.getMessage(), e);
            }
        }
    }

    private CampaignIdsByType separateCampaignsByType(PromotionCountResponse countResponse) {
        List<Long> type8Ids = new ArrayList<>();
        List<Long> type9Ids = new ArrayList<>();

        if (countResponse == null || countResponse.getAdverts() == null) {
            return new CampaignIdsByType(type8Ids, type9Ids);
        }

        for (PromotionCountResponse.AdvertGroup advertGroup : countResponse.getAdverts()) {
            Integer type = advertGroup.getType();
            if (type == null || (type != 8 && type != 9)) continue;

            Integer status = advertGroup.getStatus();
            if (status == null || (status != 7 && status != 9 && status != 11)) continue;

            if (advertGroup.getAdvertList() == null) continue;

            List<Long> targetList = type == 8 ? type8Ids : type9Ids;
            for (PromotionCountResponse.AdvertInfo advertInfo : advertGroup.getAdvertList()) {
                if (advertInfo.getAdvertId() != null) {
                    targetList.add(advertInfo.getAdvertId());
                }
            }
        }
        return new CampaignIdsByType(type8Ids, type9Ids);
    }

    /**
     * Загрузка деталей кампаний через GET /api/advert/v2/adverts батчами по 50 ID.
     */
    private List<PromotionAdvertsResponse.Campaign> fetchAdvertsV2InBatches(String apiKey, List<Long> campaignIds) {
        List<PromotionAdvertsResponse.Campaign> allCampaigns = new ArrayList<>();
        int totalBatches = (campaignIds.size() + campaignsBatchSize - 1) / campaignsBatchSize;

        log.info("Загрузка детальной информации о {} кампаниях (v2) батчами по {} (всего батчей: {})",
                campaignIds.size(), campaignsBatchSize, totalBatches);

        for (int i = 0; i < campaignIds.size(); i += campaignsBatchSize) {
            int endIndex = Math.min(i + campaignsBatchSize, campaignIds.size());
            List<Long> batch = campaignIds.subList(i, endIndex);
            int currentBatch = (i / campaignsBatchSize) + 1;

            try {
                log.info("Загрузка батча {}/{}: {} кампаний", currentBatch, totalBatches, batch.size());
                PromotionAdvertsResponse batchResponse = promotionApiClient.getAdvertsV2(apiKey, batch);
                if (batchResponse != null && batchResponse.getAdverts() != null) {
                    allCampaigns.addAll(batchResponse.getAdverts());
                    log.info("Получено {} кампаний из батча {}/{}", batchResponse.getAdverts().size(), currentBatch, totalBatches);
                }
            } catch (Exception e) {
                if (AbstractWbApiClient.isConnectionIoError(e)) {
                    log.warn("Ошибка при загрузке батча {}/{} кампаний (v2): {}", currentBatch, totalBatches, e.getMessage());
                } else {
                    log.error("Ошибка при загрузке батча {}/{} кампаний (v2): {}", currentBatch, totalBatches, e.getMessage(), e);
                }
            }
        }
        return allCampaigns;
    }

    private List<PromotionFullStatsResponse.CampaignStats> fetchStatisticsInBatches(
            String apiKey,
            List<Long> campaignIds,
            LocalDate dateFrom,
            LocalDate dateTo
    ) {
        List<PromotionFullStatsResponse.CampaignStats> allStats = new ArrayList<>();
        int totalBatches = (campaignIds.size() + statisticsBatchSize - 1) / statisticsBatchSize;
        String dateFromStr = dateFrom.format(DATE_FORMATTER);
        String dateToStr = dateTo.format(DATE_FORMATTER);

        log.info("Загрузка статистики для {} кампаний батчами по {} (всего батчей: {})",
                campaignIds.size(), statisticsBatchSize, totalBatches);

        for (int i = 0; i < campaignIds.size(); i += statisticsBatchSize) {
            int endIndex = Math.min(i + statisticsBatchSize, campaignIds.size());
            List<Long> batch = campaignIds.subList(i, endIndex);
            int currentBatch = (i / statisticsBatchSize) + 1;

            try {
                log.info("Загрузка статистики батча {}/{}: {} кампаний", currentBatch, totalBatches, batch.size());
                PromotionFullStatsRequest request = PromotionFullStatsRequest.builder()
                        .advertId(batch)
                        .dateFrom(dateFromStr)
                        .dateTo(dateToStr)
                        .build();
                PromotionFullStatsResponse batchResponse = promotionApiClient.getPromotionFullStats(apiKey, request);
                if (batchResponse != null && batchResponse.getAdverts() != null) {
                    allStats.addAll(batchResponse.getAdverts());
                    List<Long> missing = findMissingCampaignIds(batchResponse, batch);
                    if (!missing.isEmpty()) {
                        log.info("Для {} кампаний из батча {}/{} нет статистики за период {} - {}: {}",
                                missing.size(), currentBatch, totalBatches, dateFrom, dateTo, missing);
                    }
                }
            } catch (Exception e) {
                if (AbstractWbApiClient.isConnectionIoError(e)) {
                    log.warn("Ошибка при загрузке статистики батча {}/{}: {}", currentBatch, totalBatches, e.getMessage());
                } else {
                    log.error("Ошибка при загрузке статистики батча {}/{}: {}", currentBatch, totalBatches, e.getMessage(), e);
                }
            }
        }

        int totalDays = allStats.stream()
                .mapToInt(c -> c.getDays() != null ? c.getDays().size() : 0)
                .sum();
        log.info("Загружено всего {} кампаний ({} дней статистики) из {} запрошенных", allStats.size(), totalDays, campaignIds.size());
        return allStats;
    }

    private List<Long> findMissingCampaignIds(PromotionFullStatsResponse response, List<Long> requestedIds) {
        Set<Long> receivedIds = response.getAdverts().stream()
                .map(PromotionFullStatsResponse.CampaignStats::getAdvertId)
                .collect(Collectors.toSet());
        return requestedIds.stream()
                .filter(id -> !receivedIds.contains(id))
                .collect(Collectors.toList());
    }

    private record CampaignIdsByType(List<Long> type8Ids, List<Long> type9Ids) {}
}
