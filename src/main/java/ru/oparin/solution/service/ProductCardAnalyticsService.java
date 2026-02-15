package ru.oparin.solution.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.oparin.solution.dto.wb.*;
import ru.oparin.solution.model.*;
import ru.oparin.solution.repository.CabinetRepository;
import ru.oparin.solution.repository.ProductCardAnalyticsRepository;
import ru.oparin.solution.repository.ProductCardRepository;
import ru.oparin.solution.repository.PromotionCampaignRepository;
import ru.oparin.solution.service.wb.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Сервис для работы с аналитикой карточек товаров.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ProductCardAnalyticsService {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final int API_CALL_DELAY_MS = 20000; // 20 секунд для карточек и кампаний
    private static final int STATISTICS_API_CALL_DELAY_MS = 20000; // 20 секунд для статистики (лимит: 3 запроса в минуту)
    private static final int AUCTION_ADVERTS_DELAY_MS = 200; // 200 мс между запросами аукционных кампаний (лимит: 5 запросов в секунду)
    private static final int CARDS_PAGE_LIMIT = 100;
    private static final int CARDS_PAGINATION_DELAY_MS = 700; // 700 мс между запросами пагинации карточек (лимит: 100 запросов в минуту, минимум 600 мс)
    private static final int PRICES_API_CALL_DELAY_MS = 600; // 600 мс между запросами цен (лимит: 10 запросов за 6 секунд)
    private static final int PRICES_BATCH_SIZE = 1000; // Максимум товаров за один запрос

    private final CabinetRepository cabinetRepository;
    private final ProductCardRepository productCardRepository;
    private final ProductCardAnalyticsRepository analyticsRepository;
    private final WbContentApiClient contentApiClient;
    private final WbAnalyticsApiClient analyticsApiClient;
    private final ProductCardService productCardService;
    private final WbPromotionApiClient promotionApiClient;
    private final PromotionCampaignService promotionCampaignService;
    private final PromotionCampaignStatisticsService campaignStatisticsService;
    private final PromotionCampaignRepository campaignRepository;
    private final WbProductsApiClient productsApiClient;
    private final ProductPriceService productPriceService;
    private final ProductStocksService stocksService;
    private final WbOrdersApiClient ordersApiClient;

    /**
     * Обновляет все карточки и загружает аналитику за указанный период (кабинет по умолчанию продавца).
     */
    @Async("taskExecutor")
    public void updateCardsAndLoadAnalytics(User seller, String apiKey, LocalDate dateFrom, LocalDate dateTo) {
        Cabinet cabinet = cabinetRepository.findDefaultByUserId(seller.getId())
                .orElseThrow(() -> new IllegalStateException("У продавца нет кабинета по умолчанию"));
        updateCardsAndLoadAnalytics(cabinet, dateFrom, dateTo);
    }

    /**
     * Обновляет карточки и загружает аналитику для указанного кабинета (ключ берётся из кабинета).
     * Выполняется асинхронно. Данные сохраняются с привязкой к этому кабинету.
     */
    @Async("taskExecutor")
    public void updateCardsAndLoadAnalytics(Cabinet cabinet, LocalDate dateFrom, LocalDate dateTo) {
        long cabinetId = cabinet.getId();

        Cabinet managed = cabinetRepository.findByIdWithUser(cabinetId)
                .orElseThrow(() -> new IllegalStateException("Кабинет не найден: " + cabinetId));
        String apiKey = managed.getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("У кабинета (ID: {}) не задан API-ключ, обновление пропущено", cabinetId);
            return;
        }

        managed.setLastDataUpdateAt(LocalDateTime.now());
        managed.setLastDataUpdateRequestedAt(null);
        cabinetRepository.save(managed);

        User seller = managed.getUser();
        log.info("Начало обновления карточек, кампаний и загрузки аналитики для кабинета (ID: {}, продавец: {}) за период {} - {}",
                cabinetId, seller.getEmail(), dateFrom, dateTo);

        CardsListResponse cardsResponse = fetchAllCards(apiKey);
        productCardService.saveOrUpdateCards(cardsResponse, managed);

        updateProductPrices(managed, apiKey);
        updateSppFromOrders(managed, apiKey);
        updateProductStocks(managed, apiKey);

        List<Long> campaignIds = updatePromotionCampaigns(managed, apiKey);

        if (!campaignIds.isEmpty()) {
            List<Long> nonFinishedCampaignIds = filterNonFinishedCampaigns(cabinetId, campaignIds);
            if (!nonFinishedCampaignIds.isEmpty()) {
                updatePromotionCampaignStatistics(seller, apiKey, nonFinishedCampaignIds, dateFrom, dateTo);
            }
        }

        List<ProductCard> productCards = productCardRepository.findByCabinet_Id(cabinetId);
        log.info("Найдено карточек для загрузки аналитики: {}", productCards.size());

        ProcessingResult result = loadAnalyticsForAllCards(productCards, apiKey, dateFrom, dateTo);

        log.info("Завершено обновление карточек, кампаний и загрузка аналитики для кабинета (ID: {}): успешно {}, ошибок {}",
                cabinetId, result.successCount(), result.errorCount());
    }

    /**
     * Обновляет список рекламных кампаний продавца.
     *
     * @return список ID обновленных кампаний
     */
    private List<Long> updatePromotionCampaigns(User seller, String apiKey) {
        try {
            log.info("Начало обновления рекламных кампаний для продавца (ID: {}, email: {})",
                    seller.getId(), seller.getEmail());

            // Получаем список кампаний по типам и статусам
            PromotionCountResponse countResponse = promotionApiClient.getPromotionCount(apiKey);

            // Разделяем кампании по типам (8 и 9)
            CampaignIdsByType campaignsByType = separateCampaignsByType(countResponse);

            List<Long> allCampaignIds = new ArrayList<>();
            allCampaignIds.addAll(campaignsByType.type8Ids());
            allCampaignIds.addAll(campaignsByType.type9Ids());

            if (allCampaignIds.isEmpty()) {
                log.info("У продавца (ID: {}, email: {}) нет рекламных кампаний типов 8 и 9",
                        seller.getId(), seller.getEmail());
                return allCampaignIds;
            }

            log.info("Найдено {} рекламных кампаний для продавца (ID: {}, email: {}): тип 8 - {}, тип 9 - {}",
                    allCampaignIds.size(), seller.getId(), seller.getEmail(),
                    campaignsByType.type8Ids().size(), campaignsByType.type9Ids().size());

            // Получаем детальную информацию о кампаниях типа 8 (Автоматическая РК)
            List<PromotionAdvertsResponse.Campaign> type8Campaigns = new ArrayList<>();
            if (!campaignsByType.type8Ids().isEmpty()) {
                type8Campaigns = fetchCampaignsInBatches(apiKey, campaignsByType.type8Ids(), 8);
            }

            // Получаем детальную информацию о кампаниях типа 9 (Аукцион)
            List<PromotionAdvertsResponse.Campaign> type9Campaigns = new ArrayList<>();
            if (!campaignsByType.type9Ids().isEmpty()) {
                type9Campaigns = fetchAuctionCampaignsInBatches(apiKey, campaignsByType.type9Ids());
            }

            // Объединяем все кампании
            List<PromotionAdvertsResponse.Campaign> allCampaigns = new ArrayList<>();
            allCampaigns.addAll(type8Campaigns);
            allCampaigns.addAll(type9Campaigns);

            if (allCampaigns.isEmpty()) {
                log.info("Не удалось получить детальную информацию о кампаниях для продавца (ID: {}, email: {})",
                        seller.getId(), seller.getEmail());
                return allCampaignIds;
            }

            // Создаем ответ со всеми кампаниями
            PromotionAdvertsResponse advertsResponse = PromotionAdvertsResponse.builder()
                    .adverts(allCampaigns)
                    .build();

            // Сохраняем кампании в БД вместе со связями артикулов
            promotionCampaignService.saveOrUpdateCampaigns(advertsResponse, seller);

            log.info("Завершено обновление рекламных кампаний для продавца (ID: {}, email: {})",
                    seller.getId(), seller.getEmail());

            return allCampaignIds;

        } catch (Exception e) {
            log.error("Ошибка при обновлении рекламных кампаний для продавца (ID: {}, email: {}): {}",
                    seller.getId(), seller.getEmail(), e.getMessage(), e);
            return List.of();
        }
    }

    /**
     * Обновляет рекламные кампании для кабинета (сохранение с привязкой к кабинету).
     */
    private List<Long> updatePromotionCampaigns(Cabinet cabinet, String apiKey) {
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

            List<PromotionAdvertsResponse.Campaign> type8Campaigns = new ArrayList<>();
            if (!campaignsByType.type8Ids().isEmpty()) {
                type8Campaigns = fetchCampaignsInBatches(apiKey, campaignsByType.type8Ids(), 8);
            }

            List<PromotionAdvertsResponse.Campaign> type9Campaigns = new ArrayList<>();
            if (!campaignsByType.type9Ids().isEmpty()) {
                type9Campaigns = fetchAuctionCampaignsInBatches(apiKey, campaignsByType.type9Ids());
            }

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
     * Обновляет статистику рекламных кампаний продавца.
     */
    private void updatePromotionCampaignStatistics(
            User seller,
            String apiKey,
            List<Long> campaignIds,
            LocalDate dateFrom,
            LocalDate dateTo
    ) {
        try {
            log.info("Начало обновления статистики кампаний для продавца (ID: {}, email: {}) за период {} - {}",
                    seller.getId(), seller.getEmail(), dateFrom, dateTo);

            // Фильтруем кампании, для которых нужно запросить статистику
            List<Long> campaignsToFetch = filterCampaignsNeedingStatistics(campaignIds, dateFrom, dateTo);

            if (campaignsToFetch.isEmpty()) {
                log.info("Статистика для всех кампаний продавца (ID: {}, email: {}) за период {} - {} уже присутствует в БД",
                        seller.getId(), seller.getEmail(), dateFrom, dateTo);
                return;
            }

            log.info("Требуется загрузка статистики для {} из {} кампаний",
                    campaignsToFetch.size(), campaignIds.size());

            // Получаем статистику батчами (максимум 50 кампаний за запрос — лимит WB API)
            List<PromotionFullStatsResponse.CampaignStats> allStats = fetchStatisticsInBatches(
                    apiKey, campaignsToFetch, dateFrom, dateTo
            );

            if (allStats.isEmpty()) {
                log.info("Не удалось получить статистику кампаний для продавца (ID: {}, email: {})",
                        seller.getId(), seller.getEmail());
                return;
            }

            // Создаем ответ со всей статистикой
            PromotionFullStatsResponse statsResponse = PromotionFullStatsResponse.builder()
                    .adverts(allStats)
                    .build();

            // Сохраняем статистику в БД
            campaignStatisticsService.saveOrUpdateStatistics(statsResponse, seller);

            log.info("Завершено обновление статистики кампаний для продавца (ID: {}, email: {})",
                    seller.getId(), seller.getEmail());

        } catch (Exception e) {
            log.error("Ошибка при обновлении статистики кампаний для продавца (ID: {}, email: {}): {}",
                    seller.getId(), seller.getEmail(), e.getMessage(), e);
            // Не прерываем выполнение, продолжаем загрузку аналитики
        }
    }

    /**
     * Получает статистику кампаний батчами (максимум 50 кампаний за запрос).
     *
     * @param apiKey      API ключ продавца
     * @param campaignIds список ID всех кампаний
     * @param dateFrom    дата начала периода
     * @param dateTo      дата окончания периода
     * @return список всей статистики
     */
    private List<PromotionFullStatsResponse.CampaignStats> fetchStatisticsInBatches(
            String apiKey,
            List<Long> campaignIds,
            LocalDate dateFrom,
            LocalDate dateTo
    ) {
        List<PromotionFullStatsResponse.CampaignStats> allStats = new ArrayList<>();
        int batchSize = 50; // Лимит WB API: number of advert cannot be more than 50
        int totalBatches = (campaignIds.size() + batchSize - 1) / batchSize;

        log.info("Загрузка статистики для {} кампаний батчами по {} (всего батчей: {})",
                campaignIds.size(), batchSize, totalBatches);

        String dateFromStr = dateFrom.format(DATE_FORMATTER);
        String dateToStr = dateTo.format(DATE_FORMATTER);

        for (int i = 0; i < campaignIds.size(); i += batchSize) {
            int endIndex = Math.min(i + batchSize, campaignIds.size());
            List<Long> batch = campaignIds.subList(i, endIndex);
            int currentBatch = (i / batchSize) + 1;

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
                    processBatchResponse(batchResponse, batch, currentBatch, totalBatches, dateFrom, dateTo);
                }

                // Задержка между батчами (кроме последнего)
                // Лимит: 3 запроса в минуту, интервал 20 секунд
                if (endIndex < campaignIds.size()) {
                    waitBeforeStatisticsRequest();
                }

            } catch (Exception e) {
                log.error("Ошибка при загрузке статистики батча {}/{}: {}", currentBatch, totalBatches, e.getMessage(), e);
                // Продолжаем загрузку следующих батчей даже при ошибке
            }
        }

        int totalDays = allStats.stream()
                .mapToInt(campaign -> campaign.getDays() != null ? campaign.getDays().size() : 0)
                .sum();
        log.info("Загружено всего {} кампаний ({} дней статистики) из {} запрошенных кампаний",
                allStats.size(), totalDays, campaignIds.size());
        return allStats;
    }

    private void processBatchResponse(
            PromotionFullStatsResponse batchResponse,
            List<Long> requestedBatch,
            int currentBatch,
            int totalBatches,
            LocalDate dateFrom,
            LocalDate dateTo
    ) {
        int totalDays = calculateTotalDaysInResponse(batchResponse);
        log.info("Получено {} кампаний ({} дней статистики) из батча {}/{}",
                batchResponse.getAdverts().size(), totalDays, currentBatch, totalBatches);

        List<Long> missingCampaignIds = findMissingCampaignIds(batchResponse, requestedBatch);
        if (!missingCampaignIds.isEmpty()) {
            log.info("Для {} кампаний из батча {}/{} нет статистики за период {} - {}: {}",
                    missingCampaignIds.size(), currentBatch, totalBatches, dateFrom, dateTo, missingCampaignIds);
        }
    }

    private int calculateTotalDaysInResponse(PromotionFullStatsResponse response) {
        return response.getAdverts().stream()
                .mapToInt(campaign -> campaign.getDays() != null ? campaign.getDays().size() : 0)
                .sum();
    }

    private List<Long> findMissingCampaignIds(
            PromotionFullStatsResponse response,
            List<Long> requestedIds
    ) {
        Set<Long> receivedIds = response.getAdverts().stream()
                .map(PromotionFullStatsResponse.CampaignStats::getAdvertId)
                .collect(Collectors.toSet());

        return requestedIds.stream()
                .filter(id -> !receivedIds.contains(id))
                .collect(Collectors.toList());
    }

    /**
     * Фильтрует список ID кампаний, исключая завершенные.
     * Оставляет активные, на паузе и готовые к запуску кампании.
     *
     * @param cabinetId   ID кабинета
     * @param campaignIds список ID всех кампаний
     * @return список ID незавершенных кампаний
     */
    private List<Long> filterNonFinishedCampaigns(Long cabinetId, List<Long> campaignIds) {
        List<PromotionCampaign> campaigns = campaignRepository.findByCabinet_Id(cabinetId);
        return filterNonFinishedCampaignsInternal(campaigns, campaignIds);
    }

    private List<Long> filterNonFinishedCampaignsInternal(List<PromotionCampaign> campaigns, List<Long> campaignIds) {
        Set<Long> finishedCampaignIds = extractFinishedCampaignIds(campaigns);
        List<Long> nonFinishedCampaignIds = filterOutFinishedCampaigns(campaignIds, finishedCampaignIds);

        int filteredCount = campaignIds.size() - nonFinishedCampaignIds.size();
        if (filteredCount > 0) {
            log.info("Исключено {} завершенных кампаний из запроса статистики. Незавершенных кампаний: {}",
                    filteredCount, nonFinishedCampaignIds.size());
        }

        return nonFinishedCampaignIds;
    }

    private Set<Long> extractFinishedCampaignIds(List<PromotionCampaign> campaigns) {
        return campaigns.stream()
                .filter(campaign -> campaign.getStatus() == CampaignStatus.FINISHED)
                .map(PromotionCampaign::getAdvertId)
                .collect(Collectors.toSet());
    }

    private List<Long> filterOutFinishedCampaigns(List<Long> campaignIds, Set<Long> finishedCampaignIds) {
        return campaignIds.stream()
                .filter(id -> !finishedCampaignIds.contains(id))
                .collect(Collectors.toList());
    }

    /**
     * Фильтрует кампании, для которых нужно запросить статистику.
     * Исключает кампании, у которых уже есть статистика за весь запрошенный период.
     *
     * @param campaignIds список ID кампаний
     * @param dateFrom    дата начала периода
     * @param dateTo      дата окончания периода
     * @return список ID кампаний, для которых нужно запросить статистику
     */
    private List<Long> filterCampaignsNeedingStatistics(
            List<Long> campaignIds,
            LocalDate dateFrom,
            LocalDate dateTo
    ) {
        List<Long> campaignsToFetch = new ArrayList<>();
        long totalDays = calculateTotalDays(dateFrom, dateTo);

        for (Long campaignId : campaignIds) {
            if (needsStatisticsFetch(campaignId, dateFrom, dateTo, totalDays)) {
                campaignsToFetch.add(campaignId);
            }
        }

        return campaignsToFetch;
    }

    private long calculateTotalDays(LocalDate dateFrom, LocalDate dateTo) {
        return ChronoUnit.DAYS.between(dateFrom, dateTo) + 1;
    }

    private boolean needsStatisticsFetch(Long campaignId, LocalDate dateFrom, LocalDate dateTo, long totalDays) {
        List<LocalDate> existingDates = campaignStatisticsService.getExistingStatisticsDates(
                campaignId, dateFrom, dateTo
        );

        if (existingDates.size() >= totalDays) {
            log.info("Статистика для кампании advertId {} за период {} - {} уже присутствует в БД",
                    campaignId, dateFrom, dateTo);
            return false;
        }

        return true;
    }

    /**
     * Разделяет кампании по типам: тип 8 и тип 9.
     * Фильтрует только кампании со статусами 7 (Завершена), 9 (Активна), 11 (Пауза).
     */
    private CampaignIdsByType separateCampaignsByType(PromotionCountResponse countResponse) {
        List<Long> type8Ids = new ArrayList<>();
        List<Long> type9Ids = new ArrayList<>();

        if (countResponse == null || countResponse.getAdverts() == null) {
            return new CampaignIdsByType(type8Ids, type9Ids);
        }

        for (PromotionCountResponse.AdvertGroup advertGroup : countResponse.getAdverts()) {
            Integer type = advertGroup.getType();
            if (type == null || (type != 8 && type != 9)) {
                continue;
            }

            // Фильтруем только статусы 7 (Завершена), 9 (Активна), 11 (Пауза)
            Integer status = advertGroup.getStatus();
            if (status == null || (status != 7 && status != 9 && status != 11)) {
                continue;
            }

            if (advertGroup.getAdvertList() == null) {
                continue;
            }

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
     * Класс для хранения ID кампаний по типам.
     */
    private record CampaignIdsByType(List<Long> type8Ids, List<Long> type9Ids) {
    }

    /**
     * Получает детальную информацию о кампаниях батчами (максимум 50 за запрос).
     *
     * @param apiKey      API ключ продавца
     * @param campaignIds список ID всех кампаний
     * @return список всех кампаний
     */
    private List<PromotionAdvertsResponse.Campaign> fetchCampaignsInBatches(String apiKey, List<Long> campaignIds, int campaignType) {
        List<PromotionAdvertsResponse.Campaign> allCampaigns = new ArrayList<>();
        int batchSize = 50;
        int totalBatches = (campaignIds.size() + batchSize - 1) / batchSize;

        log.info("Загрузка детальной информации о {} кампаниях типа {} батчами по {} (всего батчей: {})",
                campaignIds.size(), campaignType, batchSize, totalBatches);

        for (int i = 0; i < campaignIds.size(); i += batchSize) {
            int endIndex = Math.min(i + batchSize, campaignIds.size());
            List<Long> batch = campaignIds.subList(i, endIndex);
            int currentBatch = (i / batchSize) + 1;

            try {
                log.info("Загрузка батча {}/{}: {} кампаний типа {}", currentBatch, totalBatches, batch.size(), campaignType);
                PromotionAdvertsResponse batchResponse = promotionApiClient.getPromotionAdverts(apiKey, batch);

                if (batchResponse != null && batchResponse.getAdverts() != null) {
                    allCampaigns.addAll(batchResponse.getAdverts());
                    log.info("Получено {} кампаний из батча {}/{}", batchResponse.getAdverts().size(), currentBatch, totalBatches);
                }
            } catch (Exception e) {
                log.error("Ошибка при загрузке батча {}/{} кампаний типа {}: {}", currentBatch, totalBatches, campaignType, e.getMessage(), e);
                // Продолжаем загрузку следующих батчей даже при ошибке
            }
        }

        return allCampaigns;
    }

    private List<PromotionAdvertsResponse.Campaign> fetchAuctionCampaignsInBatches(String apiKey, List<Long> campaignIds) {
        List<PromotionAdvertsResponse.Campaign> allCampaigns = new ArrayList<>();
        int batchSize = 50; // Максимум 50 ID за запрос согласно документации API
        int totalBatches = (campaignIds.size() + batchSize - 1) / batchSize;

        log.info("Загрузка детальной информации о {} аукционных кампаниях (тип 9) батчами по {} (всего батчей: {})",
                campaignIds.size(), batchSize, totalBatches);

        for (int i = 0; i < campaignIds.size(); i += batchSize) {
            int endIndex = Math.min(i + batchSize, campaignIds.size());
            List<Long> batch = campaignIds.subList(i, endIndex);
            int currentBatch = (i / batchSize) + 1;

            try {
                log.info("Загрузка батча {}/{}: {} аукционных кампаний", currentBatch, totalBatches, batch.size());
                AuctionAdvertsResponse batchResponse = promotionApiClient.getAuctionAdverts(apiKey, batch);

                if (batchResponse != null && batchResponse.getAdverts() != null) {
                    // Конвертируем аукционные кампании в формат PromotionAdvertsResponse.Campaign
                    for (AuctionAdvertsResponse.AuctionCampaign auctionCampaign : batchResponse.getAdverts()) {
                        PromotionAdvertsResponse.Campaign campaign = promotionApiClient.convertAuctionToPromotionCampaign(auctionCampaign);
                        if (campaign != null) {
                            allCampaigns.add(campaign);
                        }
                    }
                    log.info("Получено {} аукционных кампаний из батча {}/{}", batchResponse.getAdverts().size(), currentBatch, totalBatches);
                }

                // Добавляем задержку между запросами (200 мс) для соблюдения лимита: 5 запросов в секунду
                if (currentBatch < totalBatches) {
                    try {
                        Thread.sleep(AUCTION_ADVERTS_DELAY_MS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        log.warn("Прервано ожидание между запросами аукционных кампаний");
                    }
                }
            } catch (Exception e) {
                log.error("Ошибка при загрузке батча {}/{} аукционных кампаний: {}", currentBatch, totalBatches, e.getMessage(), e);
                // Продолжаем загрузку следующих батчей даже при ошибке
            }
        }

        return allCampaigns;
    }

    private CardsListResponse fetchAllCards(String apiKey) {
        CardsListRequest initialRequest = createInitialCardsRequest();
        CardsListResponse response = contentApiClient.getCardsList(apiKey, initialRequest);

        if (response.getCards() == null) {
            response.setCards(new ArrayList<>());
        }

        int totalReceived = response.getCards().size();
        Integer totalInResponse = response.getCursor() != null ? response.getCursor().getTotal() : null;
        log.info("Первая страница: получено {} карточек, total в ответе: {}", totalReceived, totalInResponse);

        int pageNumber = 1;
        // Согласно документации: повторяем, пока total >= limit (т.е. пока есть еще страницы)
        while (hasMoreCards(response, totalReceived)) {
            pageNumber++;
            // Задержка между запросами для соблюдения лимита API (100 запросов в минуту)
            try {
                Thread.sleep(CARDS_PAGINATION_DELAY_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Прервана задержка между запросами пагинации карточек");
                break;
            }

            CardsListRequest nextRequest = createNextPageRequest(response);
            CardsListResponse nextResponse = contentApiClient.getCardsList(apiKey, nextRequest);

            if (nextResponse.getCards() == null || nextResponse.getCards().isEmpty()) {
                log.info("Страница {}: пустой ответ, завершаем пагинацию", pageNumber);
                break;
            }

            int cardsOnPage = nextResponse.getCards().size();
            response.getCards().addAll(nextResponse.getCards());
            totalReceived += cardsOnPage;
            response.setCursor(nextResponse.getCursor());

            Integer nextTotal = nextResponse.getCursor() != null ? nextResponse.getCursor().getTotal() : null;
            log.info("Страница {}: получено {} карточек, total в ответе: {}, всего получено: {}",
                    pageNumber, cardsOnPage, nextTotal, totalReceived);

            // Согласно документации: если total в ответе меньше limit, значит это последняя страница
            if (nextTotal != null && nextTotal < CARDS_PAGE_LIMIT) {
                log.info("Страница {}: total ({}) < limit ({}), это последняя страница",
                        pageNumber, nextTotal, CARDS_PAGE_LIMIT);
                break;
            }
        }

        log.info("Пагинация завершена. Всего получено карточек: {}", totalReceived);
        return response;
    }

    private CardsListRequest createInitialCardsRequest() {
        CardsListRequest.Cursor cursor = CardsListRequest.Cursor.builder()
                .limit(CARDS_PAGE_LIMIT)
                .build();

        // Согласно документации: добавляем фильтр withPhoto: -1 (все карточки)
        CardsListRequest.Filter filter = CardsListRequest.Filter.builder()
                .withPhoto(-1)
                .build();

        CardsListRequest.Settings settings = CardsListRequest.Settings.builder()
                .cursor(cursor)
                .filter(filter)
                .build();

        return CardsListRequest.builder()
                .settings(settings)
                .build();
    }

    private boolean hasMoreCards(CardsListResponse response, int totalReceived) {
        if (response.getCursor() == null || response.getCursor().getTotal() == null) {
            log.debug("hasMoreCards: cursor или total отсутствует, завершаем пагинацию");
            return false;
        }

        Integer total = response.getCursor().getTotal();
        // Согласно документации: "повторяйте пункты 2 и 3, пока значение total в ответе не станет меньше чем значение limit в запросе"
        // Если total >= limit, значит есть еще страницы, продолжаем пагинацию
        boolean shouldContinue = total >= CARDS_PAGE_LIMIT;

        log.debug("hasMoreCards: total={}, limit={}, totalReceived={}, shouldContinue={}",
                total, CARDS_PAGE_LIMIT, totalReceived, shouldContinue);

        return shouldContinue;
    }

    private CardsListRequest createNextPageRequest(CardsListResponse response) {
        if (response.getCursor() == null) {
            throw new IllegalStateException("Cursor отсутствует в ответе для создания следующего запроса");
        }

        CardsListResponse.Cursor cursor = response.getCursor();
        log.debug("Создание запроса следующей страницы: nmID={}, updatedAt={}",
                cursor.getNmID(), cursor.getUpdatedAt());

        CardsListRequest.Cursor nextCursor = CardsListRequest.Cursor.builder()
                .limit(CARDS_PAGE_LIMIT)
                .nmID(cursor.getNmID())
                .updatedAt(cursor.getUpdatedAt())
                .build();

        // Сохраняем фильтр withPhoto: -1 для всех последующих запросов
        CardsListRequest.Filter filter = CardsListRequest.Filter.builder()
                .withPhoto(-1)
                .build();

        CardsListRequest.Settings nextSettings = CardsListRequest.Settings.builder()
                .cursor(nextCursor)
                .filter(filter)
                .build();

        return CardsListRequest.builder()
                .settings(nextSettings)
                .build();
    }

    private ProcessingResult loadAnalyticsForAllCards(
            List<ProductCard> cards,
            String apiKey,
            LocalDate dateFrom,
            LocalDate dateTo
    ) {
        int successCount = 0;
        int errorCount = 0;
        String dateFromStr = dateFrom.format(DATE_FORMATTER);
        String dateToStr = dateTo.format(DATE_FORMATTER);

        for (ProductCard card : cards) {
            try {
                loadAnalyticsForCard(card, apiKey, dateFromStr, dateToStr, dateFrom, dateTo);
                successCount++;
            } catch (Exception e) {
                log.error("Ошибка при загрузке аналитики для карточки nmID {}: {}",
                        card.getNmId(), e.getMessage());
                errorCount++;
            }
        }

        return new ProcessingResult(successCount, errorCount);
    }

    private void loadAnalyticsForCard(
            ProductCard card,
            String apiKey,
            String dateFromStr,
            String dateToStr,
            LocalDate dateFrom,
            LocalDate dateTo
    ) {
        Long cabinetId = card.getCabinet() != null ? card.getCabinet().getId() : null;
        List<LocalDate> existingDates = getExistingAnalyticsDates(card.getNmId(), cabinetId, dateFrom, dateTo);

        if (isAllDatesPresent(existingDates, dateFrom, dateTo)) {
            log.info("Аналитика для nmID {} за период {} - {} уже присутствует в БД",
                    card.getNmId(), dateFrom, dateTo);
            return;
        }

        DateRange requestRange = calculateRequestRange(existingDates, dateFrom, dateTo);
        if (requestRange == null) {
            return;
        }

        logMissingDatesInfo(card.getNmId(), existingDates, dateFrom, dateTo, requestRange);

        SaleFunnelResponse analyticsResponse = fetchAnalytics(
                apiKey,
                card.getNmId(),
                requestRange.from(),
                requestRange.to()
        );

        if (analyticsResponse == null || analyticsResponse.getData() == null) {
            log.warn("Аналитика для карточки nmID {} не получена", card.getNmId());
            return;
        }

        saveAnalyticsData(card, analyticsResponse, existingDates, dateFrom, dateTo);
    }

    private void logMissingDatesInfo(
            Long nmId,
            List<LocalDate> existingDates,
            LocalDate dateFrom,
            LocalDate dateTo,
            DateRange requestRange
    ) {
        if (!existingDates.isEmpty()) {
            long totalDays = ChronoUnit.DAYS.between(dateFrom, dateTo) + 1;
            log.info("Для nmID {} уже есть аналитика за {} из {} дней. Запрашиваем период {} - {}",
                    nmId, existingDates.size(), totalDays, requestRange.from(), requestRange.to());
        }
    }

    private SaleFunnelResponse fetchAnalytics(
            String apiKey,
            Long nmId,
            LocalDate dateFrom,
            LocalDate dateTo
    ) {
        // Задержка перед реальным запросом к API
        waitBeforeApiRequest();

        String dateFromStr = dateFrom.format(DATE_FORMATTER);
        String dateToStr = dateTo.format(DATE_FORMATTER);

        return analyticsApiClient.getSaleFunnelProduct(apiKey, nmId, dateFromStr, dateToStr);
    }

    /**
     * Задержка перед запросом к API для предотвращения превышения лимитов.
     */
    private void waitBeforeApiRequest() {
        try {
            Thread.sleep(API_CALL_DELAY_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Прервана задержка перед запросом к API");
        }
    }

    /**
     * Задержка перед запросом статистики кампаний.
     * Лимит: 3 запроса в минуту, интервал 20 секунд.
     */
    private void waitBeforeStatisticsRequest() {
        try {
            Thread.sleep(STATISTICS_API_CALL_DELAY_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Прервана задержка между запросами статистики");
        }
    }

    private void saveAnalyticsData(
            ProductCard card,
            SaleFunnelResponse analyticsResponse,
            List<LocalDate> existingDates,
            LocalDate dateFrom,
            LocalDate dateTo
    ) {
        int savedCount = 0;
        int updatedCount = 0;
        int skippedCount = 0;

        // Собираем данные для батчевого сохранения
        List<AnalyticsSaveItem> itemsToSave = new ArrayList<>();

        for (SaleFunnelResponse.DailyData dailyData : analyticsResponse.getData()) {
            if (dailyData == null || dailyData.getDt() == null) {
                continue;
            }

            if (!card.getNmId().equals(dailyData.getNmId())) {
                continue;
            }

            try {
                LocalDate date = LocalDate.parse(dailyData.getDt(), DATE_FORMATTER);

                if (!isDateInRange(date, dateFrom, dateTo)) {
                    continue;
                }

                if (existingDates.contains(date)) {
                    skippedCount++;
                    continue;
                }

                itemsToSave.add(new AnalyticsSaveItem(card, dailyData, date));

            } catch (Exception e) {
                log.error("Ошибка при подготовке аналитики для карточки nmID {} за дату {}: {}",
                        card.getNmId(), dailyData.getDt(), e.getMessage());
            }
        }

        // Сохраняем все данные в одной транзакции
        if (!itemsToSave.isEmpty()) {
            SaveBatchResult batchResult = saveAnalyticsBatch(itemsToSave);
            savedCount = batchResult.savedCount();
            updatedCount = batchResult.updatedCount();
        }

        log.info("Аналитика для карточки nmID {}: создано {}, обновлено {}, пропущено {}",
                card.getNmId(), savedCount, updatedCount, skippedCount);
    }

    /**
     * Сохраняет батч аналитики в отдельной транзакции.
     */
    @Transactional
    protected SaveBatchResult saveAnalyticsBatch(List<AnalyticsSaveItem> items) {
        int savedCount = 0;
        int updatedCount = 0;

        for (AnalyticsSaveItem item : items) {
            try {
                SaveResult result = saveOrUpdateAnalytics(item.card(), item.dailyData(), item.date());
                if (result.isNew()) {
                    savedCount++;
                } else {
                    updatedCount++;
                }
            } catch (Exception e) {
                log.error("Ошибка при сохранении аналитики для карточки nmID {} за дату {}: {}",
                        item.card().getNmId(), item.date(), e.getMessage());
            }
        }

        return new SaveBatchResult(savedCount, updatedCount);
    }

    /**
     * Запись для батчевого сохранения аналитики.
     */
    private record AnalyticsSaveItem(
            ProductCard card,
            SaleFunnelResponse.DailyData dailyData,
            LocalDate date
    ) {
    }

    /**
     * Результат батчевого сохранения.
     */
    private record SaveBatchResult(
            int savedCount,
            int updatedCount
    ) {
    }

    private boolean isDateInRange(LocalDate date, LocalDate dateFrom, LocalDate dateTo) {
        return !date.isBefore(dateFrom) && !date.isAfter(dateTo);
    }

    private SaveResult saveOrUpdateAnalytics(
            ProductCard card,
            SaleFunnelResponse.DailyData dailyData,
            LocalDate date
    ) {
        Long cabinetId = card.getCabinet() != null ? card.getCabinet().getId() : null;
        Optional<ProductCardAnalytics> existing = cabinetId != null
                ? analyticsRepository.findByProductCardNmIdAndDateAndCabinet_Id(card.getNmId(), date, cabinetId)
                : analyticsRepository.findByProductCardNmIdAndDate(card.getNmId(), date);

        ProductCardAnalytics analytics = existing.orElseGet(() -> {
            ProductCardAnalytics newAnalytics = new ProductCardAnalytics();
            newAnalytics.setProductCard(card);
            newAnalytics.setDate(date);
            if (card.getCabinet() != null) {
                newAnalytics.setCabinet(card.getCabinet());
            }
            return newAnalytics;
        });
        if (card.getCabinet() != null && analytics.getCabinet() == null) {
            analytics.setCabinet(card.getCabinet());
        }

        updateAnalyticsFields(analytics, dailyData);
        analyticsRepository.save(analytics);

        return new SaveResult(existing.isEmpty());
    }

    private void updateAnalyticsFields(ProductCardAnalytics analytics, SaleFunnelResponse.DailyData dailyData) {
        analytics.setOpenCard(dailyData.getOpenCardCount());
        analytics.setAddToCart(dailyData.getAddToCartCount());
        analytics.setOrders(dailyData.getOrdersCount());
        analytics.setOrdersSum(dailyData.getOrdersSumRub());
        // Конверсии больше не сохраняем в БД, рассчитываем на лету
    }

    private List<LocalDate> getExistingAnalyticsDates(Long nmId, Long cabinetId, LocalDate dateFrom, LocalDate dateTo) {
        List<ProductCardAnalytics> existing = cabinetId != null
                ? analyticsRepository.findByCabinet_IdAndProductCardNmIdAndDateBetween(cabinetId, nmId, dateFrom, dateTo)
                : analyticsRepository.findByProductCardNmIdAndDateBetween(nmId, dateFrom, dateTo);

        return existing.stream()
                .map(ProductCardAnalytics::getDate)
                .toList();
    }

    private boolean isAllDatesPresent(List<LocalDate> existingDates, LocalDate dateFrom, LocalDate dateTo) {
        long totalDays = ChronoUnit.DAYS.between(dateFrom, dateTo) + 1;

        if (existingDates.size() < totalDays) {
            return false;
        }

        for (LocalDate date = dateFrom; !date.isAfter(dateTo); date = date.plusDays(1)) {
            if (!existingDates.contains(date)) {
                return false;
            }
        }

        return true;
    }

    private DateRange calculateRequestRange(List<LocalDate> existingDates, LocalDate dateFrom, LocalDate dateTo) {
        LocalDate minMissingDate = null;
        LocalDate maxMissingDate = null;

        for (LocalDate date = dateFrom; !date.isAfter(dateTo); date = date.plusDays(1)) {
            if (!existingDates.contains(date)) {
                if (minMissingDate == null) {
                    minMissingDate = date;
                }
                maxMissingDate = date;
            }
        }

        if (minMissingDate == null || maxMissingDate == null) {
            return null;
        }

        return new DateRange(minMissingDate, maxMissingDate);
    }

    private record ProcessingResult(int successCount, int errorCount) {
    }

    private record SaveResult(boolean isNew) {
    }

    private record DateRange(LocalDate from, LocalDate to) {
    }

    /**
     * Обновляет цены товаров за вчерашнюю дату для кабинета.
     */
    private void updateProductPrices(Cabinet cabinet, String apiKey) {
        try {
            User seller = cabinet.getUser();
            LocalDate yesterdayDate = LocalDate.now().minusDays(1);
            log.info("Начало загрузки цен товаров за дату {} для кабинета (ID: {})", yesterdayDate, cabinet.getId());

            List<ProductCard> productCards = productCardRepository.findByCabinet_Id(cabinet.getId());

            if (productCards.isEmpty()) {
                log.info("У кабинета (ID: {}) нет карточек товаров для загрузки цен", cabinet.getId());
                return;
            }

            List<Long> nmIds = productCards.stream()
                    .map(ProductCard::getNmId)
                    .filter(nmId -> nmId != null)
                    .distinct()
                    .collect(Collectors.toList());

            if (nmIds.isEmpty()) {
                log.warn("Не найдено валидных nmId для загрузки цен у кабинета (ID: {})", cabinet.getId());
                return;
            }

            List<ProductPriceHistory> existingPrices = productPriceService.getPricesByNmIdsAndDate(nmIds, yesterdayDate, cabinet.getId());
            Set<Long> existingNmIds = existingPrices.stream()
                    .map(ProductPriceHistory::getNmId)
                    .collect(Collectors.toSet());

            if (existingNmIds.size() == nmIds.size()) {
                log.info("Цены за дату {} уже загружены для всех {} товаров кабинета (ID: {}). Пропускаем загрузку.",
                        yesterdayDate, nmIds.size(), cabinet.getId());
                return;
            }

            List<Long> nmIdsToLoad = nmIds.stream()
                    .filter(nmId -> !existingNmIds.contains(nmId))
                    .collect(Collectors.toList());

            if (nmIdsToLoad.isEmpty()) {
                log.info("Цены за дату {} уже загружены для всех товаров кабинета (ID: {}). Пропускаем загрузку.",
                        yesterdayDate, cabinet.getId());
                return;
            }

            log.info("Найдено {} товаров для загрузки цен у кабинета (ID: {}). Уже загружено: {}, требуется загрузить: {}",
                    nmIds.size(), cabinet.getId(), existingNmIds.size(), nmIdsToLoad.size());

            // Разбиваем на батчи по 1000 товаров
            List<List<Long>> batches = partitionList(nmIdsToLoad, PRICES_BATCH_SIZE);
            log.info("Разбито на {} батчей для загрузки цен", batches.size());

            // Загружаем цены батчами
            for (int i = 0; i < batches.size(); i++) {
                List<Long> batch = batches.get(i);
                log.info("Загрузка цен для батча {}/{} ({} товаров)", i + 1, batches.size(), batch.size());

                try {
                    ProductPricesRequest request = ProductPricesRequest.builder()
                            .nmList(batch)
                            .build();

                    ProductPricesResponse response = productsApiClient.getProductPrices(apiKey, request);
                    productPriceService.savePrices(response, yesterdayDate, cabinet);

                    // Задержка между запросами (кроме последнего)
                    if (i < batches.size() - 1) {
                        Thread.sleep(PRICES_API_CALL_DELAY_MS);
                    }
                } catch (Exception e) {
                    log.error("Ошибка при загрузке цен для батча {}/{}: {}",
                            i + 1, batches.size(), e.getMessage(), e);
                    // Продолжаем загрузку остальных батчей
                }
            }

            log.info("Завершена загрузка цен товаров за дату {} для кабинета (ID: {})", yesterdayDate, cabinet.getId());

        } catch (Exception e) {
            log.error("Ошибка при загрузке цен товаров для кабинета (ID: {}): {}", cabinet.getId(), e.getMessage(), e);
            // Не прерываем выполнение, продолжаем загрузку аналитики
        }
    }

    /**
     * Обновляет СПП (скидка постоянного покупателя) из заказов за вчерашнюю дату.
     * Получает заказы, собирает Map nmId -> spp и обновляет поле sppDiscount в product_price_history.
     */
    private void updateSppFromOrders(Cabinet cabinet, String apiKey) {
        try {
            LocalDate yesterdayDate = LocalDate.now().minusDays(1);
            log.info("Начало обновления СПП из заказов за дату {} для кабинета (ID: {})", yesterdayDate, cabinet.getId());

            List<OrdersResponse.Order> orders = ordersApiClient.getOrders(apiKey, yesterdayDate, 1);

            if (orders == null || orders.isEmpty()) {
                log.info("Не найдено заказов за дату {} для кабинета (ID: {}). Пропускаем обновление СПП.", yesterdayDate, cabinet.getId());
                return;
            }

            log.info("Получено заказов за дату {}: {} для кабинета (ID: {})", yesterdayDate, orders.size(), cabinet.getId());

            // Собираем Map: nmId -> spp
            // СПП для всех покупателей для одного товара на дату должен быть одинаковый
            Map<Long, Integer> sppByNmId = new HashMap<>();
            Map<Long, Set<Integer>> sppValuesByNmId = new HashMap<>(); // Для проверки различий

            for (OrdersResponse.Order order : orders) {
                if (order.getNmId() != null && order.getSpp() != null) {
                    Long nmId = order.getNmId();
                    Integer spp = order.getSpp();

                    // Сохраняем все уникальные значения для проверки
                    sppValuesByNmId.computeIfAbsent(nmId, k -> new HashSet<>()).add(spp);

                    // Перезаписываем значение (должно быть одинаковое для всех заказов одного товара)
                    sppByNmId.put(nmId, spp);
                }
            }

            // Проверяем, есть ли товары с разными значениями СПП
            for (Map.Entry<Long, Set<Integer>> entry : sppValuesByNmId.entrySet()) {
                if (entry.getValue().size() > 1) {
                    log.warn("Обнаружены разные значения СПП для товара nmId={} за дату {}: {}. Используется последнее значение: {}",
                            entry.getKey(), yesterdayDate, entry.getValue(), sppByNmId.get(entry.getKey()));
                }
            }

            if (sppByNmId.isEmpty()) {
                log.warn("Не найдено валидных данных СПП в заказах за дату {} для кабинета (ID: {})", yesterdayDate, cabinet.getId());
                return;
            }

            log.info("Найдено {} уникальных артикулов с данными СПП для обновления", sppByNmId.size());

            productPriceService.updateSppDiscount(sppByNmId, yesterdayDate, cabinet.getId());

            log.info("Завершено обновление СПП из заказов за дату {} для кабинета (ID: {})", yesterdayDate, cabinet.getId());

        } catch (Exception e) {
            log.error("Ошибка при обновлении СПП из заказов для кабинета (ID: {}): {}", cabinet.getId(), e.getMessage(), e);
            // Не прерываем выполнение, продолжаем загрузку аналитики
        }
    }

    /**
     * Разбивает список на части указанного размера.
     */
    private <T> List<List<T>> partitionList(List<T> list, int batchSize) {
        List<List<T>> batches = new ArrayList<>();
        for (int i = 0; i < list.size(); i += batchSize) {
            int end = Math.min(i + batchSize, list.size());
            batches.add(new ArrayList<>(list.subList(i, end)));
        }
        return batches;
    }

    /**
     * Обновляет остатки товаров на складах WB для кабинета.
     */
    private void updateProductStocks(Cabinet cabinet, String apiKey) {
        try {
            log.info("Начало обновления остатков товаров на складах WB для кабинета (ID: {})", cabinet.getId());

            List<ProductCard> productCards = productCardRepository.findByCabinet_Id(cabinet.getId());

            if (productCards.isEmpty()) {
                log.info("У кабинета (ID: {}) нет карточек товаров для обновления остатков", cabinet.getId());
                return;
            }

            List<Long> nmIds = productCards.stream()
                    .map(ProductCard::getNmId)
                    .filter(nmId -> nmId != null)
                    .distinct()
                    .collect(Collectors.toList());

            if (nmIds.isEmpty()) {
                log.warn("Не найдено валидных nmId для обновления остатков у кабинета (ID: {})", cabinet.getId());
                return;
            }

            log.info("Найдено {} товаров для обновления остатков на складах WB у кабинета (ID: {})", nmIds.size(), cabinet.getId());

            int requestCount = 0;
            for (Long nmId : nmIds) {
                try {
                    stocksService.getWbStocksBySizes(apiKey, nmId, cabinet);
                    requestCount++;

                    if (requestCount < nmIds.size()) {
                        log.info("Пауза 20 секунд перед следующим запросом остатков (лимит API: 3 запроса в минуту, интервал 20 секунд)");
                        Thread.sleep(20000);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.error("Прервано ожидание перед запросом остатков для артикула {}: {}", nmId, e.getMessage());
                    break;
                } catch (Exception e) {
                    log.error("Ошибка при обновлении остатков для артикула {}: {}", nmId, e.getMessage(), e);
                }
            }

            log.info("Завершено обновление остатков товаров на складах WB для кабинета (ID: {})", cabinet.getId());

        } catch (Exception e) {
            log.error("Ошибка при обновлении остатков товаров на складах WB для кабинета (ID: {}): {}", cabinet.getId(), e.getMessage(), e);
        }
    }
}
