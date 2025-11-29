package ru.oparin.solution.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.oparin.solution.dto.wb.CardsListRequest;
import ru.oparin.solution.dto.wb.CardsListResponse;
import ru.oparin.solution.dto.wb.SaleFunnelResponse;
import ru.oparin.solution.model.ProductCard;
import ru.oparin.solution.model.ProductCardAnalytics;
import ru.oparin.solution.model.User;
import ru.oparin.solution.repository.ProductCardAnalyticsRepository;
import ru.oparin.solution.repository.ProductCardRepository;
import ru.oparin.solution.service.wb.WbAnalyticsApiClient;
import ru.oparin.solution.service.wb.WbContentApiClient;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

/**
 * Сервис для работы с аналитикой карточек товаров.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ProductCardAnalyticsService {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final int API_CALL_DELAY_MS = 20000;
    private static final int CARDS_PAGE_LIMIT = 100;

    private final ProductCardRepository productCardRepository;
    private final ProductCardAnalyticsRepository analyticsRepository;
    private final WbContentApiClient contentApiClient;
    private final WbAnalyticsApiClient analyticsApiClient;
    private final ProductCardService productCardService;

    /**
     * Обновляет все карточки и загружает аналитику за указанный период.
     * Выполняется асинхронно с ограниченным параллелизмом (максимум 5 потоков).
     */
    @Async("taskExecutor")
    @Transactional
    public void updateCardsAndLoadAnalytics(User seller, String apiKey, LocalDate dateFrom, LocalDate dateTo) {
        log.info("Начало обновления карточек и загрузки аналитики для продавца (ID: {}, email: {}) за период {} - {}", 
                seller.getId(), seller.getEmail(), dateFrom, dateTo);

        CardsListResponse cardsResponse = fetchAllCards(apiKey);
        productCardService.saveOrUpdateCards(cardsResponse, seller);

        List<ProductCard> productCards = productCardRepository.findBySellerId(seller.getId());
        log.info("Найдено карточек для загрузки аналитики: {}", productCards.size());

        ProcessingResult result = loadAnalyticsForAllCards(productCards, apiKey, dateFrom, dateTo);
        
        log.info("Завершено обновление карточек и загрузка аналитики для продавца (ID: {}, email: {}): успешно {}, ошибок {}", 
                seller.getId(), seller.getEmail(), result.successCount(), result.errorCount());
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

        for (int i = 0; i < cards.size(); i++) {
            ProductCard card = cards.get(i);
            
            waitBetweenRequests(i);
            
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

    private void waitBetweenRequests(int currentIndex) {
        if (currentIndex == 0) {
            return;
        }

        try {
            Thread.sleep(API_CALL_DELAY_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Прервана задержка между запросами");
        }
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
        String dateFromStr = dateFrom.format(DATE_FORMATTER);
        String dateToStr = dateTo.format(DATE_FORMATTER);
        
        return analyticsApiClient.getSaleFunnelProduct(apiKey, nmId, dateFromStr, dateToStr);
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
