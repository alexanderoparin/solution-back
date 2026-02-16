package ru.oparin.solution.service.sync;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.oparin.solution.dto.wb.*;
import ru.oparin.solution.model.Cabinet;
import ru.oparin.solution.model.CampaignStatus;
import ru.oparin.solution.model.PromotionCampaign;
import ru.oparin.solution.model.User;
import ru.oparin.solution.repository.PromotionCampaignRepository;
import ru.oparin.solution.service.PromotionCampaignService;
import ru.oparin.solution.service.PromotionCampaignStatisticsService;
import ru.oparin.solution.service.wb.WbPromotionApiClient;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
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
    private static final int STATISTICS_BATCH_SIZE = 50;
    private static final int STATISTICS_API_CALL_DELAY_MS = 20000;
    private static final int CAMPAIGNS_BATCH_SIZE = 50;
    private static final int AUCTION_ADVERTS_DELAY_MS = 200;

    private final WbPromotionApiClient promotionApiClient;
    private final PromotionCampaignService promotionCampaignService;
    private final PromotionCampaignStatisticsService campaignStatisticsService;
    private final PromotionCampaignRepository campaignRepository;

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

            List<PromotionAdvertsResponse.Campaign> type8Campaigns = campaignsByType.type8Ids().isEmpty()
                    ? List.of()
                    : fetchCampaignsInBatches(apiKey, campaignsByType.type8Ids(), 8);
            List<PromotionAdvertsResponse.Campaign> type9Campaigns = campaignsByType.type9Ids().isEmpty()
                    ? List.of()
                    : fetchAuctionCampaignsInBatches(apiKey, campaignsByType.type9Ids());

            List<PromotionAdvertsResponse.Campaign> allCampaigns = new ArrayList<>();
            allCampaigns.addAll(type8Campaigns);
            allCampaigns.addAll(type9Campaigns);

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

        } catch (Exception e) {
            log.error("Ошибка при обновлении рекламных кампаний для кабинета (ID: {}): {}",
                    cabinet.getId(), e.getMessage(), e);
            return List.of();
        }
    }

    /**
     * Обновляет статистику кампаний за период. Исключает завершённые кампании и уже загруженные даты.
     */
    public void updateStatistics(
            User seller,
            String apiKey,
            List<Long> campaignIds,
            Long cabinetId,
            LocalDate dateFrom,
            LocalDate dateTo
    ) {
        try {
            log.info("Начало обновления статистики кампаний для продавца (ID: {}, email: {}) за период {} - {}",
                    seller.getId(), seller.getEmail(), dateFrom, dateTo);

            List<Long> nonFinishedIds = filterNonFinishedCampaigns(cabinetId, campaignIds);
            List<Long> campaignsToFetch = filterCampaignsNeedingStatistics(nonFinishedIds, dateFrom, dateTo);

            if (campaignsToFetch.isEmpty()) {
                log.info("Статистика для всех кампаний за период {} - {} уже присутствует в БД", dateFrom, dateTo);
                return;
            }

            log.info("Требуется загрузка статистики для {} из {} кампаний", campaignsToFetch.size(), campaignIds.size());

            List<PromotionFullStatsResponse.CampaignStats> allStats = fetchStatisticsInBatches(
                    apiKey, campaignsToFetch, dateFrom, dateTo);

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
            log.error("Ошибка при обновлении статистики кампаний для продавца (ID: {}, email: {}): {}",
                    seller.getId(), seller.getEmail(), e.getMessage(), e);
        }
    }

    public List<Long> filterNonFinishedCampaigns(Long cabinetId, List<Long> campaignIds) {
        List<PromotionCampaign> campaigns = campaignRepository.findByCabinet_Id(cabinetId);
        Set<Long> finishedIds = campaigns.stream()
                .filter(c -> c.getStatus() == CampaignStatus.FINISHED)
                .map(PromotionCampaign::getAdvertId)
                .collect(Collectors.toSet());
        List<Long> nonFinished = campaignIds.stream()
                .filter(id -> !finishedIds.contains(id))
                .collect(Collectors.toList());
        if (campaignIds.size() - nonFinished.size() > 0) {
            log.info("Исключено {} завершённых кампаний из запроса статистики. Незавершённых: {}",
                    campaignIds.size() - nonFinished.size(), nonFinished.size());
        }
        return nonFinished;
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

    private List<PromotionAdvertsResponse.Campaign> fetchCampaignsInBatches(String apiKey, List<Long> campaignIds, int campaignType) {
        List<PromotionAdvertsResponse.Campaign> allCampaigns = new ArrayList<>();
        int totalBatches = (campaignIds.size() + CAMPAIGNS_BATCH_SIZE - 1) / CAMPAIGNS_BATCH_SIZE;

        log.info("Загрузка детальной информации о {} кампаниях типа {} батчами по {} (всего батчей: {})",
                campaignIds.size(), campaignType, CAMPAIGNS_BATCH_SIZE, totalBatches);

        for (int i = 0; i < campaignIds.size(); i += CAMPAIGNS_BATCH_SIZE) {
            int endIndex = Math.min(i + CAMPAIGNS_BATCH_SIZE, campaignIds.size());
            List<Long> batch = campaignIds.subList(i, endIndex);
            int currentBatch = (i / CAMPAIGNS_BATCH_SIZE) + 1;

            try {
                log.info("Загрузка батча {}/{}: {} кампаний типа {}", currentBatch, totalBatches, batch.size(), campaignType);
                PromotionAdvertsResponse batchResponse = promotionApiClient.getPromotionAdverts(apiKey, batch);
                if (batchResponse != null && batchResponse.getAdverts() != null) {
                    allCampaigns.addAll(batchResponse.getAdverts());
                    log.info("Получено {} кампаний из батча {}/{}", batchResponse.getAdverts().size(), currentBatch, totalBatches);
                }
            } catch (Exception e) {
                log.error("Ошибка при загрузке батча {}/{} кампаний типа {}: {}", currentBatch, totalBatches, campaignType, e.getMessage(), e);
            }
        }
        return allCampaigns;
    }

    private List<PromotionAdvertsResponse.Campaign> fetchAuctionCampaignsInBatches(String apiKey, List<Long> campaignIds) {
        List<PromotionAdvertsResponse.Campaign> allCampaigns = new ArrayList<>();
        int totalBatches = (campaignIds.size() + CAMPAIGNS_BATCH_SIZE - 1) / CAMPAIGNS_BATCH_SIZE;

        log.info("Загрузка детальной информации о {} аукционных кампаниях (тип 9) батчами по {} (всего батчей: {})",
                campaignIds.size(), CAMPAIGNS_BATCH_SIZE, totalBatches);

        for (int i = 0; i < campaignIds.size(); i += CAMPAIGNS_BATCH_SIZE) {
            int endIndex = Math.min(i + CAMPAIGNS_BATCH_SIZE, campaignIds.size());
            List<Long> batch = campaignIds.subList(i, endIndex);
            int currentBatch = (i / CAMPAIGNS_BATCH_SIZE) + 1;

            try {
                log.info("Загрузка батча {}/{}: {} аукционных кампаний", currentBatch, totalBatches, batch.size());
                AuctionAdvertsResponse batchResponse = promotionApiClient.getAuctionAdverts(apiKey, batch);
                if (batchResponse != null && batchResponse.getAdverts() != null) {
                    for (AuctionAdvertsResponse.AuctionCampaign ac : batchResponse.getAdverts()) {
                        PromotionAdvertsResponse.Campaign campaign = promotionApiClient.convertAuctionToPromotionCampaign(ac);
                        if (campaign != null) allCampaigns.add(campaign);
                    }
                    log.info("Получено {} аукционных кампаний из батча {}/{}", batchResponse.getAdverts().size(), currentBatch, totalBatches);
                }
                if (currentBatch < totalBatches) {
                    SyncDelayUtil.sleep(AUCTION_ADVERTS_DELAY_MS);
                }
            } catch (Exception e) {
                log.error("Ошибка при загрузке батча {}/{} аукционных кампаний: {}", currentBatch, totalBatches, e.getMessage(), e);
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
        int totalBatches = (campaignIds.size() + STATISTICS_BATCH_SIZE - 1) / STATISTICS_BATCH_SIZE;
        String dateFromStr = dateFrom.format(DATE_FORMATTER);
        String dateToStr = dateTo.format(DATE_FORMATTER);

        log.info("Загрузка статистики для {} кампаний батчами по {} (всего батчей: {})",
                campaignIds.size(), STATISTICS_BATCH_SIZE, totalBatches);

        for (int i = 0; i < campaignIds.size(); i += STATISTICS_BATCH_SIZE) {
            int endIndex = Math.min(i + STATISTICS_BATCH_SIZE, campaignIds.size());
            List<Long> batch = campaignIds.subList(i, endIndex);
            int currentBatch = (i / STATISTICS_BATCH_SIZE) + 1;

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
                if (endIndex < campaignIds.size()) {
                    SyncDelayUtil.sleep(STATISTICS_API_CALL_DELAY_MS);
                }
            } catch (Exception e) {
                log.error("Ошибка при загрузке статистики батча {}/{}: {}", currentBatch, totalBatches, e.getMessage(), e);
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

    private List<Long> filterCampaignsNeedingStatistics(List<Long> campaignIds, LocalDate dateFrom, LocalDate dateTo) {
        long totalDays = ChronoUnit.DAYS.between(dateFrom, dateTo) + 1;
        List<Long> toFetch = new ArrayList<>();
        for (Long campaignId : campaignIds) {
            List<LocalDate> existingDates = campaignStatisticsService.getExistingStatisticsDates(campaignId, dateFrom, dateTo);
            if (existingDates.size() < totalDays) {
                toFetch.add(campaignId);
            } else {
                log.info("Статистика для кампании advertId {} за период {} - {} уже в БД", campaignId, dateFrom, dateTo);
            }
        }
        return toFetch;
    }

    private record CampaignIdsByType(List<Long> type8Ids, List<Long> type9Ids) {}
}
