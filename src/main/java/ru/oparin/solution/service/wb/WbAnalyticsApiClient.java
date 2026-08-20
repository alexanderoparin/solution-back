package ru.oparin.solution.service.wb;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import ru.oparin.solution.dto.wb.*;
import ru.oparin.solution.model.CabinetTokenType;
import ru.oparin.solution.model.WbApiEventType;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * Клиент для работы с Analytics API Wildberries.
 * Эндпоинты: аналитика воронки продаж.
 * Категория WB API: Аналитика.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class WbAnalyticsApiClient extends AbstractWbApiClient {

    @Override
    protected WbApiCategory getApiCategory() {
        return WbApiCategory.ANALYTICS;
    }

    private static final int MAX_ANALYTICS_PERIOD_DAYS = 7;
    private static final int ITEM_RATING_PAGE_LIMIT = 1000;

    @Value("${wb.retries.max-429-basic}")
    private int maxRetries429Basic;
    @Value("${wb.retries.max-429-personal}")
    private int maxRetries429Personal;

    private final WbApiTokenTypeResolver tokenTypeResolver;

    /**
     * Отчёт «Оценка товара» — одна страница (POST /api/analytics/v2/item-rating).
     * Период: вчера — вчера (снимок текущего рейтинга по правилам API).
     *
     * @param apiKey API-ключ (категория «Аналитика»)
     * @param offset смещение пагинации
     * @return ответ с data.items
     */
    public WbItemRatingResponse postItemRating(String apiKey, int offset) {
        String fullUrl = WbApiEventType.ANALYTICS_ITEM_RATING_CABINET.getDefaultUrl();
        logWbApiCall(fullUrl, "оценка товара item-rating", null);

        LocalDate yesterday = LocalDate.now().minusDays(1);
        String date = yesterday.format(DateTimeFormatter.ISO_LOCAL_DATE);
        WbItemRatingRequest request = WbItemRatingRequest.builder()
                .currentPeriod(WbItemRatingRequest.Period.builder().start(date).end(date).build())
                .orderBy(WbItemRatingRequest.OrderBy.builder().field("feedbackRating").mode("desc").build())
                .limit(ITEM_RATING_PAGE_LIMIT)
                .offset(offset)
                .build();

        CabinetTokenType tokenType = tokenTypeResolver.resolveByApiKey(apiKey);
        int maxRetries429 = tokenType == CabinetTokenType.PERSONAL ? maxRetries429Personal : maxRetries429Basic;
        long retryDelayMs429 = WbApiEventType.ANALYTICS_ITEM_RATING_CABINET.getRequestDelayMs(tokenType);

        try {
            ResponseEntity<String> response = executeWithConnectionRetry(
                    "оценка товара WB item-rating offset=" + offset,
                    () -> executeWithRetry(
                            fullUrl,
                            apiKey,
                            request,
                            maxRetries429,
                            retryDelayMs429,
                            "оценка товара item-rating"
                    )
            );
            return objectMapper.readValue(response.getBody(), WbItemRatingResponse.class);
        } catch (HttpClientErrorException e) {
            throwIf401ScopeNotAllowed(e);
            logWbApiError("оценка товара WB item-rating offset=" + offset, e);
            throw new RestClientException("Ошибка при получении отчёта item-rating: " + e.getMessage(), e);
        } catch (RestClientException e) {
            throw e;
        } catch (Exception e) {
            log.error("Ошибка при парсинге ответа item-rating offset={}: {}", offset, e.getMessage());
            throw new RestClientException("Ошибка при парсинге ответа item-rating: " + e.getMessage(), e);
        }
    }

    /**
     * Пауза между страницами item-rating в legacy-синхронизации (один поток).
     */
    public void delayBetweenWbItemRatingRequests(String apiKey) {
        CabinetTokenType tokenType = tokenTypeResolver.resolveByApiKey(apiKey);
        long ms = Math.max(1L, WbApiEventType.ANALYTICS_ITEM_RATING_CABINET.getRequestDelayMs(tokenType));
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Прервана пауза между запросами item-rating WB", e);
        }
    }

    /**
     * Получение аналитики воронки продаж по карточке товара.
     */
    public WbSaleFunnelResponse getSaleFunnelProduct(String apiKey, Long nmId, String dateFrom, String dateTo) {
        String fullUrl = WbApiEventType.ANALYTICS_SALES_FUNNEL_NMID.getDefaultUrl();
        logWbApiCall(fullUrl, "воронка продаж по карточке", nmId);

        LocalDate validatedFromDate = validateAndAdjustDateFrom(dateFrom, nmId);
        LocalDate validatedToDate = validateAndAdjustDateTo(dateTo, nmId);
        if (validatedFromDate.isAfter(validatedToDate)) {
            // Весь запрошенный период старше разрешённого — обрезаем до последних 7 дней
            validatedToDate = LocalDate.now().minusDays(1);
            validatedFromDate = validatedToDate.minusDays(MAX_ANALYTICS_PERIOD_DAYS - 1);
            log.warn("Период выходит за пределы допустимого API (макс. последние 7 дней). Обрезаем до {} - {} nmID={}",
                    validatedFromDate, validatedToDate, nmId);
        }
        WbSaleFunnelHistoryRequest request = buildWbAnalyticsRequest(nmId, validatedFromDate, validatedToDate);
        CabinetTokenType tokenType = tokenTypeResolver.resolveByApiKey(apiKey);
        int maxRetries429 = tokenType == CabinetTokenType.PERSONAL ? maxRetries429Personal : maxRetries429Basic;
        long retryDelayMs429 = WbApiEventType.ANALYTICS_SALES_FUNNEL_NMID.getRequestDelayMs(tokenType);

        try {
            // Сначала даём executeWithRetry обрабатывать 429 (Too Many Requests),
            // а поверх него — executeWithConnectionRetry для таймаутов/сетевых ошибок.
            ResponseEntity<String> response = executeWithConnectionRetry(
                    "аналитика воронки продаж WB nmID=" + nmId,
                    () -> executeWithRetry(
                            fullUrl,
                            apiKey,
                            request,
                            maxRetries429,
                            retryDelayMs429,
                            "воронка продаж по карточке"
                    )
            );
            return parseAnalyticsResponse(response, nmId);
        } catch (HttpClientErrorException e) {
            throwIf401ScopeNotAllowed(e);
            logWbApiError("аналитика воронки продаж WB nmID=" + nmId, e);
            throw new RestClientException("Ошибка при получении аналитики воронки продаж: " + e.getMessage(), e);
        }
    }

    /**
     * API отдаёт данные максимум за последнюю неделю (7 дней).
     * Дата начала не может быть раньше (вчера − 6 дней).
     */
    private LocalDate validateAndAdjustDateFrom(String dateFrom, Long nmId) {
        LocalDate from = LocalDate.parse(dateFrom);
        LocalDate yesterday = LocalDate.now().minusDays(1);
        LocalDate earliestAllowed = yesterday.minusDays(MAX_ANALYTICS_PERIOD_DAYS - 1); // 6 дней назад = 7 дней с вчера
        if (from.isBefore(earliestAllowed)) {
            return earliestAllowed;
        }
        return from;
    }

    private LocalDate validateAndAdjustDateTo(String dateTo, Long nmId) {
        LocalDate toDate = LocalDate.parse(dateTo);
        // API позволяет получать данные максимум до вчера
        LocalDate yesterday = LocalDate.now().minusDays(1);
        
        if (toDate.isAfter(yesterday)) {
            log.warn("Дата окончания {} в будущем или сегодня, ограничиваем до вчера ({}) nmID={}", toDate, yesterday, nmId);
            return yesterday;
        }
        
        return toDate;
    }

    private WbSaleFunnelHistoryRequest buildWbAnalyticsRequest(Long nmId, LocalDate fromDate, LocalDate toDate) {
        // API позволяет получать данные максимум за неделю (7 дней включая обе даты)
        // ChronoUnit.DAYS.between не включает обе даты, поэтому для 7 дней включая обе даты нужно daysBetween <= 6
        long daysBetween = ChronoUnit.DAYS.between(fromDate, toDate);
        
        if (daysBetween >= MAX_ANALYTICS_PERIOD_DAYS) {
            log.warn("Период превышает {} дней ({} дней между датами), ограничиваем дату начала nmID={}", 
                    MAX_ANALYTICS_PERIOD_DAYS, daysBetween, nmId);
            // minusDays(6) даст 7 дней включая обе даты (например, с 1 по 7 января)
            fromDate = toDate.minusDays(MAX_ANALYTICS_PERIOD_DAYS - 1);
        }
        
        WbSaleFunnelHistoryRequest.Period period = WbSaleFunnelHistoryRequest.Period.builder()
                .start(fromDate.format(DateTimeFormatter.ISO_LOCAL_DATE))
                .end(toDate.format(DateTimeFormatter.ISO_LOCAL_DATE))
                .build();
        
        return WbSaleFunnelHistoryRequest.builder()
                .selectedPeriod(period)
                .nmIds(List.of(nmId))
                .skipDeletedNm(false)
                .aggregationLevel("day")
                .build();
    }

    private WbSaleFunnelResponse parseAnalyticsResponse(ResponseEntity<String> response, Long nmId) {
        try {
            List<WbSaleFunnelHistoryResponse> historyResponses = objectMapper.readValue(
                    response.getBody(),
                    objectMapper.getTypeFactory().constructCollectionType(
                            List.class, 
                            WbSaleFunnelHistoryResponse.class
                    )
            );
            
            return convertToWbSaleFunnelResponse(historyResponses, nmId);
                    
        } catch (Exception e) {
            log.error("Ошибка при парсинге ответа аналитики для nmID {}: {}", nmId, e.getMessage());
            throw new RestClientException("Ошибка при парсинге ответа аналитики: " + e.getMessage(), e);
        }
    }

    private WbSaleFunnelResponse convertToWbSaleFunnelResponse(
            List<WbSaleFunnelHistoryResponse> historyResponses, 
            Long nmId
    ) {
        List<WbSaleFunnelResponse.DailyData> dailyDataList = new ArrayList<>();
        
        for (WbSaleFunnelHistoryResponse response : historyResponses) {
            if (response == null || response.getProduct() == null || response.getHistory() == null) {
                continue;
            }
            
            if (!nmId.equals(response.getProduct().getNmId())) {
                continue;
            }
            
            for (WbSaleFunnelHistoryResponse.HistoryItem item : response.getHistory()) {
                if (item == null || item.getDate() == null) {
                    continue;
                }
                
                WbSaleFunnelResponse.DailyData dailyData = WbSaleFunnelResponse.DailyData.builder()
                        .nmId(response.getProduct().getNmId())
                        .dt(item.getDate())
                        .openCardCount(item.getOpenCount())
                        .addToCartCount(item.getCartCount())
                        .ordersCount(item.getOrderCount())
                        .ordersSumRub(item.getOrderSum())
                        .addToCartConversion(item.getAddToCartConversion())
                        .cartToOrderConversion(item.getCartToOrderConversion())
                        .build();
                
                dailyDataList.add(dailyData);
            }
        }
        
        return WbSaleFunnelResponse.builder()
                .data(dailyDataList)
                .build();
    }
}

