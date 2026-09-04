package ru.oparin.solution.service.sync;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import ru.oparin.solution.dto.wb.*;
import ru.oparin.solution.exception.WbApiUnauthorizedScopeException;
import ru.oparin.solution.exception.WbRateLimitDeferException;
import ru.oparin.solution.model.*;
import ru.oparin.solution.repository.WbCampaignArticleRepository;
import ru.oparin.solution.repository.WbPromotionCampaignRepository;
import ru.oparin.solution.repository.WbPromotionCampaignStatisticsRepository;
import ru.oparin.solution.service.WbPromotionCampaignService;
import ru.oparin.solution.service.WbPromotionCampaignStatisticsService;
import ru.oparin.solution.service.WbPromotionNormQueryStatisticsService;
import ru.oparin.solution.service.wb.AbstractWbApiClient;
import ru.oparin.solution.service.wb.WbApiTokenTypeResolver;
import ru.oparin.solution.service.wb.WbPromotionApiClient;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Синхронизация рекламных кампаний и их статистики с WB API.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WbPromotionCampaignSyncService {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    @Value("${wb.promotion.statistics-batch-size-basic}")
    private int statisticsBatchSizeBasic;
    @Value("${wb.promotion.statistics-batch-size-personal}")
    private int statisticsBatchSizePersonal;
    @Value("${wb.promotion.campaigns-batch-size-basic}")
    private int campaignsBatchSizeBasic;
    @Value("${wb.promotion.campaigns-batch-size-personal}")
    private int campaignsBatchSizePersonal;
    @Value("${wb.promotion.normquery-items-batch-size}")
    private int normqueryItemsBatchSize;
    @Value("${wb.promotion.normquery-campaigns-batch-size-basic}")
    private int normqueryCampaignsBatchSizeBasic;
    @Value("${wb.promotion.normquery-campaigns-batch-size-personal}")
    private int normqueryCampaignsBatchSizePersonal;

    private final WbPromotionApiClient promotionApiClient;
    private final WbPromotionCampaignService promotionCampaignService;
    private final WbPromotionCampaignStatisticsService campaignStatisticsService;
    private final WbPromotionNormQueryStatisticsService normQueryStatisticsService;
    private final WbPromotionCampaignRepository campaignRepository;
    private final WbCampaignArticleRepository campaignArticleRepository;
    private final WbPromotionCampaignStatisticsRepository campaignStatisticsRepository;
    private final WbApiTokenTypeResolver tokenTypeResolver;

    public WbPromotionCountResponse fetchPromotionCount(String apiKey) {
        return promotionApiClient.getPromotionCount(apiKey);
    }

    public List<Long> listCampaignIdsFromCount(WbPromotionCountResponse countResponse) {
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
        WbPromotionAdvertsResponse batchResponse = promotionApiClient.getAdvertsV2(apiKey, batchIds);
        if (batchResponse != null && batchResponse.getAdverts() != null && !batchResponse.getAdverts().isEmpty()) {
            promotionCampaignService.saveOrUpdateCampaigns(
                    WbPromotionAdvertsResponse.builder().adverts(batchResponse.getAdverts()).build(),
                    cabinet
            );
        }
    }

    /**
     * Один запрос fullstats и сохранение по advertId батча.
     */
    public void loadAndSaveStatisticsBatch(
            User seller,
            String apiKey,
            List<Long> batchIds,
            LocalDate dateFrom,
            LocalDate dateTo
    ) {
        List<Long> advertIds = batchIds == null ? List.of() : batchIds.stream().filter(Objects::nonNull).distinct().toList();
        if (advertIds.isEmpty()) {
            log.info("Батч fullstats пустой, запрос WB пропущен");
            return;
        }
        String dateFromStr = dateFrom.format(DATE_FORMATTER);
        String dateToStr = dateTo.format(DATE_FORMATTER);
        WbPromotionFullStatsRequest request = WbPromotionFullStatsRequest.builder()
                .advertId(advertIds)
                .dateFrom(dateFromStr)
                .dateTo(dateToStr)
                .build();
        WbPromotionFullStatsResponse batchResponse = promotionApiClient.getPromotionFullStats(apiKey, request);
        if (batchResponse != null && batchResponse.getAdverts() != null && !batchResponse.getAdverts().isEmpty()) {
            campaignStatisticsService.saveOrUpdateStatistics(
                    WbPromotionFullStatsResponse.builder().adverts(batchResponse.getAdverts()).build(),
                    seller
            );
        }
    }

    /**
     * Группа advertId одного ранга для fullstats/normquery: 0 — активные, 1 — пауза, 2 — остальные.
     *
     * @param rank ранг синхронизации статистики
     * @param advertIds идентификаторы кампаний группы в стабильном порядке
     */
    public record StatisticsSyncIdGroup(int rank, List<Long> advertIds) {
    }

    /**
     * AdvertId кампаний кабинета для fullstats и следующих батчей normquery за период.
     * Порядок: активные, затем пауза, затем остальные.
     *
     * @param dateFrom dateTo зарезервированы под контекст периода в очереди; отбор по датам не выполняется
     */
    @SuppressWarnings("unused")
    public List<Long> listCampaignIdsNeedingStatisticsForPeriod(Long cabinetId, LocalDate dateFrom, LocalDate dateTo) {
        return listCampaignIdGroupsForStatisticsSync(cabinetId).stream()
                .map(StatisticsSyncIdGroup::advertIds)
                .flatMap(List::stream)
                .toList();
    }

    /**
     * AdvertId кабинета, сгруппированные для статистики: активные, пауза, остальные.
     * Группы не смешиваются; внутри группы порядок стабилен по advertId.
     *
     * @param cabinetId идентификатор кабинета
     * @return непустые группы в порядке приоритета синхронизации
     */
    public List<StatisticsSyncIdGroup> listCampaignIdGroupsForStatisticsSync(Long cabinetId) {
        List<WbPromotionCampaign> campaigns = new ArrayList<>(campaignRepository.findByCabinet_Id(cabinetId));
        campaigns.sort(Comparator
                .comparingInt((WbPromotionCampaign campaign) -> statisticsSyncRank(campaign.getStatus()))
                .thenComparing(WbPromotionCampaign::getAdvertId, Comparator.nullsLast(Long::compareTo)));

        LinkedHashSet<Long> seen = new LinkedHashSet<>();
        List<Long> currentIds = new ArrayList<>();
        int currentRank = -1;
        List<StatisticsSyncIdGroup> groups = new ArrayList<>();
        for (WbPromotionCampaign campaign : campaigns) {
            Long advertId = campaign.getAdvertId();
            if (advertId == null || !seen.add(advertId)) {
                continue;
            }
            int rank = statisticsSyncRank(campaign.getStatus());
            if (currentRank >= 0 && rank != currentRank) {
                groups.add(new StatisticsSyncIdGroup(currentRank, List.copyOf(currentIds)));
                currentIds = new ArrayList<>();
            }
            currentRank = rank;
            currentIds.add(advertId);
        }
        if (!currentIds.isEmpty()) {
            groups.add(new StatisticsSyncIdGroup(currentRank, List.copyOf(currentIds)));
        }
        return groups;
    }

    /**
     * Ранг очереди статистики: активные раньше паузы, пауза раньше остальных.
     *
     * @param status статус РК из БД
     * @return 0 — активна, 1 — пауза, 2 — остальные
     */
    public static int statisticsSyncRank(WbCampaignStatus status) {
        if (status == WbCampaignStatus.ACTIVE) {
            return 0;
        }
        if (status == WbCampaignStatus.PAUSED) {
            return 1;
        }
        return 2;
    }

    public int getCampaignsBatchSize() {
        return campaignsBatchSizeBasic;
    }

    public int getStatisticsBatchSize() {
        return statisticsBatchSizeBasic;
    }

    public int getCampaignsBatchSize(CabinetTokenType tokenType) {
        return tokenType == CabinetTokenType.PERSONAL ? campaignsBatchSizePersonal : campaignsBatchSizeBasic;
    }

    public int getStatisticsBatchSize(CabinetTokenType tokenType) {
        return tokenType == CabinetTokenType.PERSONAL ? statisticsBatchSizePersonal : statisticsBatchSizeBasic;
    }

    public int getNormqueryCampaignsBatchSize(CabinetTokenType tokenType) {
        return tokenType == CabinetTokenType.PERSONAL
                ? normqueryCampaignsBatchSizePersonal
                : normqueryCampaignsBatchSizeBasic;
    }

    /**
     * Загрузка и сохранение статистики поисковых кластеров для батча кампаний.
     */
    public void loadAndSaveNormQueryStatsBatch(
            String apiKey,
            List<Long> campaignIds,
            LocalDate dateFrom,
            LocalDate dateTo
    ) {
        if (campaignIds == null || campaignIds.isEmpty()) {
            return;
        }
        List<WbNormQueryStatsRequest.Item> items = buildNormQueryItems(campaignIds);
        if (items.isEmpty()) {
            log.info("Нет пар advertId/nmId для normquery stats, кампании: {}", campaignIds);
            return;
        }
        String from = dateFrom.format(DATE_FORMATTER);
        String to = dateTo.format(DATE_FORMATTER);
        CabinetTokenType tokenType = tokenTypeResolver.resolveByApiKey(apiKey);
        boolean singleRequestPerRun = tokenType != CabinetTokenType.PERSONAL;
        WbNormQueryStatsResponse merged = WbNormQueryStatsResponse.builder().items(new ArrayList<>()).build();
        for (int i = 0; i < items.size(); i += normqueryItemsBatchSize) {
            int end = Math.min(i + normqueryItemsBatchSize, items.size());
            List<WbNormQueryStatsRequest.Item> chunk = items.subList(i, end);
            WbNormQueryStatsRequest request = WbNormQueryStatsRequest.builder()
                    .from(from)
                    .to(to)
                    .items(chunk)
                    .build();
            try {
                WbNormQueryStatsResponse batchResponse = promotionApiClient.postNormQueryStats(apiKey, request);
                if (batchResponse != null && batchResponse.getItems() != null) {
                    merged.getItems().addAll(batchResponse.getItems());
                }
            } catch (Exception e) {
                WbRateLimitDeferException defer = WbRateLimitDeferException.findInChain(e);
                if (defer != null) {
                    log.warn(
                            "Ошибка при загрузке normquery stats: {} (отложено до {})",
                            defer.getMessage(),
                            defer.getDeferUntil()
                    );
                    throw defer;
                }
                if (AbstractWbApiClient.isSoftLoggedWbError(e)) {
                    log.warn("Ошибка при загрузке normquery stats: {}", e.getMessage());
                } else {
                    log.error("Ошибка при загрузке normquery stats: {}", e.getMessage(), e);
                }
            }
            if (singleRequestPerRun) {
                if (items.size() > normqueryItemsBatchSize) {
                    log.info(
                            "Базовый токен WB: за один запуск загружен первый чанк normquery ({} из {} пар), "
                                    + "остальные — при следующих событиях очереди (пауза 30 мин)",
                            normqueryItemsBatchSize,
                            items.size()
                    );
                }
                break;
            }
        }
        normQueryStatisticsService.replaceStatisticsForCampaigns(merged, campaignIds, dateFrom, dateTo);
    }

    private List<WbNormQueryStatsRequest.Item> buildNormQueryItems(List<Long> campaignIds) {
        List<WbCampaignArticle> articles = campaignArticleRepository.findByCampaignIdIn(campaignIds);
        List<WbNormQueryStatsRequest.Item> items = new ArrayList<>();
        if (!articles.isEmpty()) {
            for (WbCampaignArticle article : articles) {
                items.add(WbNormQueryStatsRequest.Item.builder()
                        .advertId(article.getCampaignId())
                        .nmId(article.getNmId())
                        .build());
            }
            return items;
        }
        for (Long campaignId : campaignIds) {
            List<Long> nmIds = campaignStatisticsRepository.findDistinctNmIdsByCampaignAdvertId(campaignId);
            for (Long nmId : nmIds) {
                items.add(WbNormQueryStatsRequest.Item.builder()
                        .advertId(campaignId)
                        .nmId(nmId)
                        .build());
            }
        }
        return items;
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

            WbPromotionCountResponse countResponse = promotionApiClient.getPromotionCount(apiKey);
            CampaignIdsByType campaignsByType = separateCampaignsByType(countResponse);

            List<Long> allCampaignIds = new ArrayList<>();
            allCampaignIds.addAll(campaignsByType.type8Ids());
            allCampaignIds.addAll(campaignsByType.type9Ids());

            if (allCampaignIds.isEmpty()) {
                log.info("У кабинета (ID: {}) нет рекламных кампаний типов 8 и 9", cabinet.getId());
                return allCampaignIds;
            }

            List<WbPromotionAdvertsResponse.Campaign> allCampaigns = fetchAdvertsV2InBatches(apiKey, allCampaignIds);

            if (allCampaigns.isEmpty()) {
                log.info("Не удалось получить детальную информацию о кампаниях для кабинета (ID: {})", cabinet.getId());
                return allCampaignIds;
            }

            WbPromotionAdvertsResponse advertsResponse = WbPromotionAdvertsResponse.builder()
                    .adverts(allCampaigns)
                    .build();
            promotionCampaignService.saveOrUpdateCampaigns(advertsResponse, cabinet);

            log.info("Завершено обновление рекламных кампаний для кабинета (ID: {})", cabinet.getId());
            return allCampaignIds;

        } catch (WbApiUnauthorizedScopeException e) {
            throw e;
        } catch (Exception e) {
            if (AbstractWbApiClient.isSoftLoggedWbError(e)) {
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

            List<WbPromotionFullStatsResponse.CampaignStats> allStats = fetchStatisticsInBatches(
                    apiKey, toFetch, dateFrom, dateTo);

            if (allStats.isEmpty()) {
                log.info("Не удалось получить статистику кампаний для продавца (ID: {})", seller.getId());
                return;
            }

            WbPromotionFullStatsResponse statsResponse = WbPromotionFullStatsResponse.builder()
                    .adverts(allStats)
                    .build();
            campaignStatisticsService.saveOrUpdateStatistics(statsResponse, seller);

            log.info("Завершено обновление статистики кампаний для продавца (ID: {})", seller.getId());

        } catch (Exception e) {
            if (AbstractWbApiClient.isSoftLoggedWbError(e)) {
                log.warn("Ошибка при обновлении статистики кампаний для продавца (ID: {}, email: {}): {}",
                        seller.getId(), seller.getEmail(), e.getMessage());
            } else {
                log.error("Ошибка при обновлении статистики кампаний для продавца (ID: {}, email: {}): {}",
                        seller.getId(), seller.getEmail(), e.getMessage(), e);
            }
        }
    }

    private CampaignIdsByType separateCampaignsByType(WbPromotionCountResponse countResponse) {
        List<Long> type8Ids = new ArrayList<>();
        List<Long> type9Ids = new ArrayList<>();

        if (countResponse == null || countResponse.getAdverts() == null) {
            return new CampaignIdsByType(type8Ids, type9Ids);
        }

        for (WbPromotionCountResponse.AdvertGroup advertGroup : countResponse.getAdverts()) {
            Integer type = advertGroup.getType();
            if (type == null || (type != 8 && type != 9)) continue;

            Integer status = advertGroup.getStatus();
            if (status == null || (status != 7 && status != 9 && status != 11)) continue;

            if (advertGroup.getAdvertList() == null) continue;

            List<Long> targetList = type == 8 ? type8Ids : type9Ids;
            for (WbPromotionCountResponse.AdvertInfo advertInfo : advertGroup.getAdvertList()) {
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
    private List<WbPromotionAdvertsResponse.Campaign> fetchAdvertsV2InBatches(String apiKey, List<Long> campaignIds) {
        List<WbPromotionAdvertsResponse.Campaign> allCampaigns = new ArrayList<>();
        int campaignsBatchSize = campaignsBatchSizeBasic;
        int totalBatches = (campaignIds.size() + campaignsBatchSize - 1) / campaignsBatchSize;

        log.info("Загрузка детальной информации о {} кампаниях (v2) батчами по {} (всего батчей: {})",
                campaignIds.size(), campaignsBatchSize, totalBatches);

        for (int i = 0; i < campaignIds.size(); i += campaignsBatchSize) {
            int endIndex = Math.min(i + campaignsBatchSize, campaignIds.size());
            List<Long> batch = campaignIds.subList(i, endIndex);
            int currentBatch = (i / campaignsBatchSize) + 1;

            try {
                log.info("Загрузка батча {}/{}: {} кампаний", currentBatch, totalBatches, batch.size());
                WbPromotionAdvertsResponse batchResponse = promotionApiClient.getAdvertsV2(apiKey, batch);
                if (batchResponse != null && batchResponse.getAdverts() != null) {
                    allCampaigns.addAll(batchResponse.getAdverts());
                    log.info("Получено {} кампаний из батча {}/{}", batchResponse.getAdverts().size(), currentBatch, totalBatches);
                }
            } catch (Exception e) {
                if (AbstractWbApiClient.isSoftLoggedWbError(e)) {
                    log.warn("Ошибка при загрузке батча {}/{} кампаний (v2): {}", currentBatch, totalBatches, e.getMessage());
                } else {
                    log.error("Ошибка при загрузке батча {}/{} кампаний (v2): {}", currentBatch, totalBatches, e.getMessage(), e);
                }
            }
        }
        return allCampaigns;
    }

    private List<WbPromotionFullStatsResponse.CampaignStats> fetchStatisticsInBatches(
            String apiKey,
            List<Long> campaignIds,
            LocalDate dateFrom,
            LocalDate dateTo
    ) {
        List<WbPromotionFullStatsResponse.CampaignStats> allStats = new ArrayList<>();
        int statisticsBatchSize = statisticsBatchSizeBasic;
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
                WbPromotionFullStatsRequest request = WbPromotionFullStatsRequest.builder()
                        .advertId(batch)
                        .dateFrom(dateFromStr)
                        .dateTo(dateToStr)
                        .build();
                WbPromotionFullStatsResponse batchResponse = promotionApiClient.getPromotionFullStats(apiKey, request);
                if (batchResponse != null && batchResponse.getAdverts() != null) {
                    allStats.addAll(batchResponse.getAdverts());
                    List<Long> missing = findMissingCampaignIds(batchResponse, batch);
                    if (!missing.isEmpty()) {
                        log.info("Для {} кампаний из батча {}/{} нет статистики за период {} - {}: {}",
                                missing.size(), currentBatch, totalBatches, dateFrom, dateTo, missing);
                    }
                }
            } catch (Exception e) {
                if (AbstractWbApiClient.isSoftLoggedWbError(e)) {
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

    private List<Long> findMissingCampaignIds(WbPromotionFullStatsResponse response, List<Long> requestedIds) {
        Set<Long> receivedIds = response.getAdverts().stream()
                .map(WbPromotionFullStatsResponse.CampaignStats::getAdvertId)
                .collect(Collectors.toSet());
        return requestedIds.stream()
                .filter(id -> !receivedIds.contains(id))
                .collect(Collectors.toList());
    }

    private record CampaignIdsByType(List<Long> type8Ids, List<Long> type9Ids) {}
}
