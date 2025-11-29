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
import ru.oparin.solution.model.PromotionCampaign;
import ru.oparin.solution.model.User;
import ru.oparin.solution.repository.ProductCardAnalyticsRepository;
import ru.oparin.solution.repository.ProductCardRepository;
import ru.oparin.solution.repository.PromotionCampaignRepository;
import ru.oparin.solution.service.wb.WbAnalyticsApiClient;
import ru.oparin.solution.service.wb.WbContentApiClient;
import ru.oparin.solution.service.wb.WbPromotionApiClient;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
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
    private static final int CARDS_PAGE_LIMIT = 100;

    private final ProductCardRepository productCardRepository;
    private final ProductCardAnalyticsRepository analyticsRepository;
    private final WbContentApiClient contentApiClient;
    private final WbAnalyticsApiClient analyticsApiClient;
    private final ProductCardService productCardService;
    private final WbPromotionApiClient promotionApiClient;
    private final PromotionCampaignService promotionCampaignService;
    private final PromotionCampaignStatisticsService campaignStatisticsService;
    private final PromotionCampaignRepository campaignRepository;

    /**
     * Обновляет все карточки и загружает аналитику за указанный период.
     * Выполняется асинхронно с ограниченным параллелизмом (максимум 5 потоков).
     */
    @Async("taskExecutor")
    @Transactional
    public void updateCardsAndLoadAnalytics(User seller, String apiKey, LocalDate dateFrom, LocalDate dateTo) {
        log.info("Начало обновления карточек, кампаний и загрузки аналитики для продавца (ID: {}, email: {}) за период {} - {}", 
                seller.getId(), seller.getEmail(), dateFrom, dateTo);

        // Обновление карточек товаров
        CardsListResponse cardsResponse = fetchAllCards(apiKey);
        productCardService.saveOrUpdateCards(cardsResponse, seller);

        // Обновление рекламных кампаний
        List<Long> campaignIds = updatePromotionCampaigns(seller, apiKey);

        // Загрузка статистики по кампаниям (исключая завершенные)
        if (!campaignIds.isEmpty()) {
            List<Long> activeCampaignIds = filterActiveCampaigns(seller.getId(), campaignIds);
            if (!activeCampaignIds.isEmpty()) {
                updatePromotionCampaignStatistics(seller, apiKey, activeCampaignIds, dateFrom, dateTo);
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
            
            // Собираем все ID кампаний из всех групп
            List<Long> campaignIds = extractCampaignIds(countResponse);
            
            if (campaignIds.isEmpty()) {
                log.info("У продавца (ID: {}, email: {}) нет рекламных кампаний", 
                        seller.getId(), seller.getEmail());
                return campaignIds;
            }

            log.info("Найдено {} рекламных кампаний для продавца (ID: {}, email: {})", 
                    campaignIds.size(), seller.getId(), seller.getEmail());

            // Получаем детальную информацию о кампаниях батчами (максимум 50 за запрос)
            List<PromotionAdvertsResponse.Campaign> allCampaigns = fetchCampaignsInBatches(apiKey, campaignIds);

            if (allCampaigns.isEmpty()) {
                log.info("Не удалось получить детальную информацию о кампаниях для продавца (ID: {}, email: {})", 
                        seller.getId(), seller.getEmail());
                return campaignIds;
            }

            // Создаем ответ со всеми кампаниями
            PromotionAdvertsResponse advertsResponse = PromotionAdvertsResponse.builder()
                    .adverts(allCampaigns)
                    .build();

            // Сохраняем кампании в БД
            promotionCampaignService.saveOrUpdateCampaigns(advertsResponse, seller);

            log.info("Завершено обновление рекламных кампаний для продавца (ID: {}, email: {})", 
                    seller.getId(), seller.getEmail());

            return campaignIds;

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
     * Фильтрует список ID кампаний, оставляя только активные (не завершенные).
     *
     * @param sellerId ID продавца
     * @param campaignIds список ID всех кампаний
     * @return список ID активных кампаний
     */
    private List<Long> filterActiveCampaigns(Long sellerId, List<Long> campaignIds) {
        List<PromotionCampaign> campaigns = campaignRepository.findBySellerId(sellerId);
        
        Set<Long> finishedCampaignIds = extractFinishedCampaignIds(campaigns);
        List<Long> activeCampaignIds = filterOutFinishedCampaigns(campaignIds, finishedCampaignIds);
        
        int filteredCount = campaignIds.size() - activeCampaignIds.size();
        if (filteredCount > 0) {
            log.info("Исключено {} завершенных кампаний из запроса статистики. Активных кампаний: {}",
                    filteredCount, activeCampaignIds.size());
        }
        
        return activeCampaignIds;
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
     * Извлекает все ID кампаний из ответа со списком кампаний.
     */
    private List<Long> extractCampaignIds(PromotionCountResponse countResponse) {
        List<Long> campaignIds = new java.util.ArrayList<>();
        
        if (countResponse == null || countResponse.getAdverts() == null) {
            return campaignIds;
        }

        for (PromotionCountResponse.AdvertGroup advertGroup : countResponse.getAdverts()) {
            if (advertGroup.getAdvertList() == null) {
                continue;
            }
            for (PromotionCountResponse.AdvertInfo advertInfo : advertGroup.getAdvertList()) {
                if (advertInfo.getAdvertId() != null) {
                    campaignIds.add(advertInfo.getAdvertId());
                }
            }
        }

        return campaignIds;
    }

    /**
     * Получает детальную информацию о кампаниях батчами (максимум 50 за запрос).
     *
     * @param apiKey API ключ продавца
     * @param campaignIds список ID всех кампаний
     * @return список всех кампаний
     */
    private List<PromotionAdvertsResponse.Campaign> fetchCampaignsInBatches(String apiKey, List<Long> campaignIds) {
        List<PromotionAdvertsResponse.Campaign> allCampaigns = new java.util.ArrayList<>();
        int batchSize = 50;
        int totalBatches = (campaignIds.size() + batchSize - 1) / batchSize;

        log.info("Загрузка детальной информации о {} кампаниях батчами по {} (всего батчей: {})", 
                campaignIds.size(), batchSize, totalBatches);

        for (int i = 0; i < campaignIds.size(); i += batchSize) {
            int endIndex = Math.min(i + batchSize, campaignIds.size());
            List<Long> batch = campaignIds.subList(i, endIndex);
            int currentBatch = (i / batchSize) + 1;

            try {
                log.info("Загрузка батча {}/{}: {} кампаний", currentBatch, totalBatches, batch.size());
                PromotionAdvertsResponse batchResponse = promotionApiClient.getPromotionAdverts(apiKey, batch);

                if (batchResponse != null && batchResponse.getAdverts() != null) {
                    allCampaigns.addAll(batchResponse.getAdverts());
                    log.info("Получено {} кампаний из батча {}/{}", batchResponse.getAdverts().size(), currentBatch, totalBatches);
                }

                // Задержка между батчами (кроме последнего)
                if (endIndex < campaignIds.size()) {
                    waitBeforeApiRequest();
                }

            } catch (Exception e) {
                log.error("Ошибка при загрузке батча {}/{}: {}", currentBatch, totalBatches, e.getMessage(), e);
                // Продолжаем загрузку следующих батчей даже при ошибке
            }
        }

        log.info("Загружено всего {} кампаний из {} запрошенных", allCampaigns.size(), campaignIds.size());
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

                SaveResult result = saveOrUpdateAnalytics(card, dailyData, date);
                if (result.isNew()) {
                    savedCount++;
                } else {
                    updatedCount++;
                }

            } catch (Exception e) {
                log.error("Ошибка при сохранении аналитики для карточки nmID {} за дату {}: {}", 
                        card.getNmId(), dailyData.getDt(), e.getMessage());
            }
        }

        log.info("Аналитика для карточки nmID {}: создано {}, обновлено {}, пропущено {}", 
                card.getNmId(), savedCount, updatedCount, skippedCount);
    }

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
        analyticsRepository.flush();

        return new SaveResult(existing.isEmpty());
    }

    private void updateAnalyticsFields(ProductCardAnalytics analytics, SaleFunnelResponse.DailyData dailyData) {
        analytics.setOpenCard(dailyData.getOpenCardCount());
        analytics.setAddToCart(dailyData.getAddToCartCount());
        analytics.setOrders(dailyData.getOrdersCount());
        analytics.setOrdersSum(dailyData.getOrdersSumRub());
        analytics.setCartToOrder(dailyData.getCartToOrderConversion());
        analytics.setOpenCardToCart(dailyData.getAddToCartConversion());
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
}
