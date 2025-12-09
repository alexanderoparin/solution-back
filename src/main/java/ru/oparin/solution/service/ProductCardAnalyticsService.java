package ru.oparin.solution.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.oparin.solution.dto.wb.CardsListRequest;
import ru.oparin.solution.dto.wb.CardsListResponse;
import ru.oparin.solution.dto.wb.PromotionAdvertsResponse;
import ru.oparin.solution.dto.wb.PromotionCountResponse;
import ru.oparin.solution.dto.wb.PromotionFullStatsRequest;
import ru.oparin.solution.dto.wb.PromotionFullStatsResponse;
import ru.oparin.solution.dto.wb.SaleFunnelResponse;
import ru.oparin.solution.model.CampaignStatus;
import ru.oparin.solution.model.ProductCard;
import ru.oparin.solution.model.ProductCardAnalytics;
import ru.oparin.solution.model.ProductPriceHistory;
import ru.oparin.solution.model.PromotionCampaign;
import ru.oparin.solution.model.User;
import ru.oparin.solution.repository.ProductCardAnalyticsRepository;
import ru.oparin.solution.repository.ProductCardRepository;
import ru.oparin.solution.repository.PromotionCampaignRepository;
import ru.oparin.solution.service.wb.WbAnalyticsApiClient;
import ru.oparin.solution.service.wb.WbContentApiClient;
import ru.oparin.solution.service.wb.WbPromotionApiClient;
import ru.oparin.solution.service.wb.WbProductsApiClient;
import ru.oparin.solution.dto.wb.ProductPricesRequest;
import ru.oparin.solution.dto.wb.ProductPricesResponse;
import ru.oparin.solution.service.ProductStocksService;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
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
    private static final int PRICES_API_CALL_DELAY_MS = 600; // 600 мс между запросами цен (лимит: 10 запросов за 6 секунд)
    private static final int PRICES_BATCH_SIZE = 1000; // Максимум товаров за один запрос

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

    /**
     * Обновляет все карточки и загружает аналитику за указанный период.
     * Выполняется асинхронно с ограниченным параллелизмом (максимум 5 потоков).
     * Каждая операция сохранения выполняется в отдельной транзакции для независимости обновлений.
     */
    @Async("taskExecutor")
    public void updateCardsAndLoadAnalytics(User seller, String apiKey, LocalDate dateFrom, LocalDate dateTo) {
        log.info("Начало обновления карточек, кампаний и загрузки аналитики для продавца (ID: {}, email: {}) за период {} - {}", 
                seller.getId(), seller.getEmail(), dateFrom, dateTo);

        // Обновление карточек товаров
        CardsListResponse cardsResponse = fetchAllCards(apiKey);
        productCardService.saveOrUpdateCards(cardsResponse, seller);

        // Загрузка цен товаров за вчерашнюю дату
        updateProductPrices(seller, apiKey);

        // Обновление остатков товаров
        updateProductStocks(seller, apiKey);

        // Обновление рекламных кампаний
        List<Long> campaignIds = updatePromotionCampaigns(seller, apiKey);

        // Загрузка статистики по кампаниям (исключая завершенные)
        if (!campaignIds.isEmpty()) {
            List<Long> nonFinishedCampaignIds = filterNonFinishedCampaigns(seller.getId(), campaignIds);
            if (!nonFinishedCampaignIds.isEmpty()) {
                updatePromotionCampaignStatistics(seller, apiKey, nonFinishedCampaignIds, dateFrom, dateTo);
            }
        }

        // Загрузка аналитики по карточкам
        List<ProductCard> productCards = productCardRepository.findBySellerId(seller.getId());
        log.info("Найдено карточек для загрузки аналитики: {}", productCards.size());

        ProcessingResult result = loadAnalyticsForAllCards(productCards, apiKey, dateFrom, dateTo);
        
        log.info("Завершено обновление карточек, кампаний и загрузка аналитики для продавца (ID: {}, email: {}): успешно {}, ошибок {}", 
                seller.getId(), seller.getEmail(), result.successCount(), result.errorCount());
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
            
            List<Long> allCampaignIds = new java.util.ArrayList<>();
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
            List<PromotionAdvertsResponse.Campaign> type8Campaigns = new java.util.ArrayList<>();
            if (!campaignsByType.type8Ids().isEmpty()) {
                type8Campaigns = fetchCampaignsInBatches(apiKey, campaignsByType.type8Ids(), 8);
            }

            // Получаем детальную информацию о кампаниях типа 9 (Аукцион)
            List<PromotionAdvertsResponse.Campaign> type9Campaigns = new java.util.ArrayList<>();
            if (!campaignsByType.type9Ids().isEmpty()) {
                type9Campaigns = fetchAuctionCampaignsInBatches(apiKey, campaignsByType.type9Ids());
            }

            // Объединяем все кампании
            List<PromotionAdvertsResponse.Campaign> allCampaigns = new java.util.ArrayList<>();
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
            // Не прерываем выполнение, продолжаем загрузку аналитики
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

            // Получаем статистику батчами (максимум 100 кампаний за запрос)
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
     * @param apiKey API ключ продавца
     * @param campaignIds список ID всех кампаний
     * @param dateFrom дата начала периода
     * @param dateTo дата окончания периода
     * @return список всей статистики
     */
    private List<PromotionFullStatsResponse.CampaignStats> fetchStatisticsInBatches(
            String apiKey,
            List<Long> campaignIds,
            LocalDate dateFrom,
            LocalDate dateTo
    ) {
        List<PromotionFullStatsResponse.CampaignStats> allStats = new java.util.ArrayList<>();
        int batchSize = 100; // Максимум 100 кампаний согласно документации
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
     * @param sellerId ID продавца
     * @param campaignIds список ID всех кампаний
     * @return список ID незавершенных кампаний
     */
    private List<Long> filterNonFinishedCampaigns(Long sellerId, List<Long> campaignIds) {
        List<PromotionCampaign> campaigns = campaignRepository.findBySellerId(sellerId);
        
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
     * @param dateFrom дата начала периода
     * @param dateTo дата окончания периода
     * @return список ID кампаний, для которых нужно запросить статистику
     */
    private List<Long> filterCampaignsNeedingStatistics(
            List<Long> campaignIds,
            LocalDate dateFrom,
            LocalDate dateTo
    ) {
        List<Long> campaignsToFetch = new java.util.ArrayList<>();
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
        List<Long> type8Ids = new java.util.ArrayList<>();
        List<Long> type9Ids = new java.util.ArrayList<>();
        
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
    private record CampaignIdsByType(List<Long> type8Ids, List<Long> type9Ids) {}

    /**
     * Получает детальную информацию о кампаниях батчами (максимум 50 за запрос).
     *
     * @param apiKey API ключ продавца
     * @param campaignIds список ID всех кампаний
     * @return список всех кампаний
     */
    private List<PromotionAdvertsResponse.Campaign> fetchCampaignsInBatches(String apiKey, List<Long> campaignIds, int campaignType) {
        List<PromotionAdvertsResponse.Campaign> allCampaigns = new java.util.ArrayList<>();
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
        List<PromotionAdvertsResponse.Campaign> allCampaigns = new java.util.ArrayList<>();
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
                ru.oparin.solution.dto.wb.AuctionAdvertsResponse batchResponse = promotionApiClient.getAuctionAdverts(apiKey, batch);

                if (batchResponse != null && batchResponse.getAdverts() != null) {
                    // Конвертируем аукционные кампании в формат PromotionAdvertsResponse.Campaign
                    for (ru.oparin.solution.dto.wb.AuctionAdvertsResponse.AuctionCampaign auctionCampaign : batchResponse.getAdverts()) {
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

        while (hasMoreCards(response)) {
            CardsListRequest nextRequest = createNextPageRequest(response);
            CardsListResponse nextResponse = contentApiClient.getCardsList(apiKey, nextRequest);

            if (nextResponse.getCards() == null || nextResponse.getCards().isEmpty()) {
                break;
            }

            response.getCards().addAll(nextResponse.getCards());
            response.setCursor(nextResponse.getCursor());
        }

        return response;
    }

    private CardsListRequest createInitialCardsRequest() {
        CardsListRequest.Cursor cursor = CardsListRequest.Cursor.builder()
                .limit(CARDS_PAGE_LIMIT)
                .build();

        CardsListRequest.Settings settings = CardsListRequest.Settings.builder()
                .cursor(cursor)
                .build();

        return CardsListRequest.builder()
                .settings(settings)
                .build();
    }

    private boolean hasMoreCards(CardsListResponse response) {
        return response.getCursor() != null 
                && response.getCards() != null 
                && response.getCards().size() < response.getCursor().getTotal();
    }

    private CardsListRequest createNextPageRequest(CardsListResponse response) {
        CardsListRequest.Cursor nextCursor = CardsListRequest.Cursor.builder()
                .limit(CARDS_PAGE_LIMIT)
                .nmID(response.getCursor().getNmID())
                .updatedAt(response.getCursor().getUpdatedAt())
                .build();

        CardsListRequest.Settings nextSettings = CardsListRequest.Settings.builder()
                .cursor(nextCursor)
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
        List<LocalDate> existingDates = getExistingAnalyticsDates(card.getNmId(), dateFrom, dateTo);
        
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
    private SaveBatchResult saveAnalyticsBatch(List<AnalyticsSaveItem> items) {
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
    ) {}

    /**
     * Результат батчевого сохранения.
     */
    private record SaveBatchResult(
            int savedCount,
            int updatedCount
    ) {}

    private boolean isDateInRange(LocalDate date, LocalDate dateFrom, LocalDate dateTo) {
        return !date.isBefore(dateFrom) && !date.isAfter(dateTo);
    }

    private SaveResult saveOrUpdateAnalytics(
            ProductCard card, 
            SaleFunnelResponse.DailyData dailyData, 
            LocalDate date
    ) {
        Optional<ProductCardAnalytics> existing = analyticsRepository
                .findByProductCardNmIdAndDate(card.getNmId(), date);

        ProductCardAnalytics analytics = existing.orElseGet(() -> {
            ProductCardAnalytics newAnalytics = new ProductCardAnalytics();
            newAnalytics.setProductCard(card);
            newAnalytics.setDate(date);
            return newAnalytics;
        });

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

    private List<LocalDate> getExistingAnalyticsDates(Long nmId, LocalDate dateFrom, LocalDate dateTo) {
        List<ProductCardAnalytics> existing = analyticsRepository
                .findByProductCardNmIdAndDateBetween(nmId, dateFrom, dateTo);

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
     * Обновляет цены товаров за вчерашнюю дату.
     */
    private void updateProductPrices(User seller, String apiKey) {
        try {
            LocalDate yesterdayDate = LocalDate.now().minusDays(1);
            log.info("Начало загрузки цен товаров за дату {} для продавца (ID: {}, email: {})", 
                    yesterdayDate, seller.getId(), seller.getEmail());

            // Получаем список всех карточек продавца
            List<ProductCard> productCards = productCardRepository.findBySellerId(seller.getId());
            
            if (productCards.isEmpty()) {
                log.info("У продавца (ID: {}, email: {}) нет карточек товаров для загрузки цен", 
                        seller.getId(), seller.getEmail());
                return;
            }

            // Собираем список nmId
            List<Long> nmIds = productCards.stream()
                    .map(ProductCard::getNmId)
                    .filter(nmId -> nmId != null)
                    .distinct()
                    .collect(Collectors.toList());

            if (nmIds.isEmpty()) {
                log.warn("Не найдено валидных nmId для загрузки цен у продавца (ID: {}, email: {})", 
                        seller.getId(), seller.getEmail());
                return;
            }

            // Проверяем, есть ли уже цены за вчерашнюю дату для всех товаров
            List<ProductPriceHistory> existingPrices = productPriceService.getPricesByNmIdsAndDate(nmIds, yesterdayDate);
            Set<Long> existingNmIds = existingPrices.stream()
                    .map(ProductPriceHistory::getNmId)
                    .collect(Collectors.toSet());

            if (existingNmIds.size() == nmIds.size()) {
                log.info("Цены за дату {} уже загружены для всех {} товаров продавца (ID: {}, email: {}). Пропускаем загрузку.", 
                        yesterdayDate, nmIds.size(), seller.getId(), seller.getEmail());
                return;
            }

            // Фильтруем товары, для которых еще нет цен
            List<Long> nmIdsToLoad = nmIds.stream()
                    .filter(nmId -> !existingNmIds.contains(nmId))
                    .collect(Collectors.toList());

            if (nmIdsToLoad.isEmpty()) {
                log.info("Цены за дату {} уже загружены для всех товаров продавца (ID: {}, email: {}). Пропускаем загрузку.", 
                        yesterdayDate, seller.getId(), seller.getEmail());
                return;
            }

            log.info("Найдено {} товаров для загрузки цен у продавца (ID: {}, email: {}). Уже загружено: {}, требуется загрузить: {}", 
                    nmIds.size(), seller.getId(), seller.getEmail(), existingNmIds.size(), nmIdsToLoad.size());

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
                    productPriceService.savePrices(response, yesterdayDate);

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

            log.info("Завершена загрузка цен товаров за дату {} для продавца (ID: {}, email: {})", 
                    yesterdayDate, seller.getId(), seller.getEmail());

        } catch (Exception e) {
            log.error("Ошибка при загрузке цен товаров для продавца (ID: {}, email: {}): {}", 
                    seller.getId(), seller.getEmail(), e.getMessage(), e);
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
     * Обновляет остатки товаров на складах WB (не на складах продавца).
     */
    private void updateProductStocks(User seller, String apiKey) {
        try {
            log.info("Начало обновления остатков товаров на складах WB для продавца (ID: {}, email: {})", 
                    seller.getId(), seller.getEmail());

            // Получаем список всех карточек продавца
            List<ProductCard> productCards = productCardRepository.findBySellerId(seller.getId());
            
            if (productCards.isEmpty()) {
                log.info("У продавца (ID: {}, email: {}) нет карточек товаров для обновления остатков", 
                        seller.getId(), seller.getEmail());
                return;
            }

            // Собираем список nmId
            List<Long> nmIds = productCards.stream()
                    .map(ProductCard::getNmId)
                    .filter(nmId -> nmId != null)
                    .distinct()
                    .collect(Collectors.toList());

            if (nmIds.isEmpty()) {
                log.warn("Не найдено валидных nmId для обновления остатков у продавца (ID: {}, email: {})", 
                        seller.getId(), seller.getEmail());
                return;
            }

            log.info("Найдено {} товаров для обновления остатков на складах WB у продавца (ID: {}, email: {})", 
                    nmIds.size(), seller.getId(), seller.getEmail());

            // Загружаем остатки по размерам для каждого товара
            // Лимит: 3 запроса в минуту, интервал 20 секунд между запросами
            int requestCount = 0;
            for (Long nmId : nmIds) {
                try {
                    stocksService.getWbStocksBySizes(apiKey, nmId);
                    requestCount++;
                    
                    // Делаем паузу 20 секунд между запросами (кроме последнего)
                    // Лимит API: 3 запроса в минуту, интервал 20 секунд
                    if (requestCount < nmIds.size()) {
                        log.info("Пауза 20 секунд перед следующим запросом остатков (лимит API: 3 запроса в минуту, интервал 20 секунд)");
                        Thread.sleep(20000); // 20 секунд
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.error("Прервано ожидание перед запросом остатков для артикула {}: {}", nmId, e.getMessage());
                    break;
                } catch (Exception e) {
                    log.error("Ошибка при обновлении остатков для артикула {}: {}", nmId, e.getMessage(), e);
                    // Продолжаем обновление остатков для других товаров
                }
            }

            log.info("Завершено обновление остатков товаров на складах WB для продавца (ID: {}, email: {})", 
                    seller.getId(), seller.getEmail());

        } catch (Exception e) {
            log.error("Ошибка при обновлении остатков товаров на складах WB для продавца (ID: {}, email: {}): {}", 
                    seller.getId(), seller.getEmail(), e.getMessage(), e);
            // Не прерываем выполнение, продолжаем загрузку аналитики
        }
    }
}
