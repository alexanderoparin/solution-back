package ru.oparin.solution.service.wb;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import ru.oparin.solution.dto.wb.SaleFunnelHistoryRequest;
import ru.oparin.solution.dto.wb.SaleFunnelHistoryResponse;
import ru.oparin.solution.dto.wb.SaleFunnelResponse;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * Клиент для работы с Analytics API Wildberries.
 * Эндпоинты: аналитика воронки продаж.
 */
@Service
@Slf4j
public class WbAnalyticsApiClient extends AbstractWbApiClient {

    private static final String SALE_FUNNEL_PRODUCT_HISTORY_ENDPOINT = "/api/analytics/v3/sales-funnel/products/history";
    private static final int MAX_RETRIES_429 = 5;
    private static final long RETRY_DELAY_MS_429 = 20000;
    private static final int MAX_ANALYTICS_PERIOD_DAYS = 7;

    @Value("${wb.api.analytics-base-url}")
    private String analyticsBaseUrl;

    /**
     * Получение аналитики воронки продаж по карточке товара.
     */
    public SaleFunnelResponse getSaleFunnelProduct(String apiKey, Long nmId, String dateFrom, String dateTo) {
        String fullUrl = analyticsBaseUrl + SALE_FUNNEL_PRODUCT_HISTORY_ENDPOINT;
        
        log.info("Запрос аналитики воронки продаж: {}", fullUrl);
        
        LocalDate validatedFromDate = validateAndAdjustDateFrom(dateFrom);
        LocalDate validatedToDate = validateAndAdjustDateTo(dateTo);
        
        SaleFunnelHistoryRequest request = buildAnalyticsRequest(nmId, validatedFromDate, validatedToDate);
        
        try {
            ResponseEntity<String> response = executeWithRetry(
                    fullUrl,
                    apiKey,
                    request,
                    MAX_RETRIES_429,
                    RETRY_DELAY_MS_429
            );
            return parseAnalyticsResponse(response, nmId);
        } catch (HttpClientErrorException e) {
            logWbApiError("аналитика воронки продаж WB", e);
            throw new RestClientException("Ошибка при получении аналитики воронки продаж: " + e.getMessage(), e);
        }
    }

    private LocalDate validateAndAdjustDateFrom(String dateFrom) {
        return LocalDate.parse(dateFrom);
    }

    private LocalDate validateAndAdjustDateTo(String dateTo) {
        LocalDate toDate = LocalDate.parse(dateTo);
        // API позволяет получать данные максимум до вчера
        LocalDate yesterday = LocalDate.now().minusDays(1);
        
        if (toDate.isAfter(yesterday)) {
            log.warn("Дата окончания {} в будущем или сегодня, ограничиваем до вчера ({})", toDate, yesterday);
            return yesterday;
        }
        
        return toDate;
    }

    private SaleFunnelHistoryRequest buildAnalyticsRequest(Long nmId, LocalDate fromDate, LocalDate toDate) {
        // API позволяет получать данные максимум за неделю (7 дней включая обе даты)
        // ChronoUnit.DAYS.between не включает обе даты, поэтому для 7 дней включая обе даты нужно daysBetween <= 6
        long daysBetween = ChronoUnit.DAYS.between(fromDate, toDate);
        
        if (daysBetween >= MAX_ANALYTICS_PERIOD_DAYS) {
            log.warn("Период превышает {} дней ({} дней между датами, что означает больше {} дней включая обе даты), ограничиваем дату начала", 
                    MAX_ANALYTICS_PERIOD_DAYS, daysBetween, MAX_ANALYTICS_PERIOD_DAYS);
            // minusDays(6) даст 7 дней включая обе даты (например, с 1 по 7 января)
            fromDate = toDate.minusDays(MAX_ANALYTICS_PERIOD_DAYS - 1);
        }
        
        SaleFunnelHistoryRequest.Period period = SaleFunnelHistoryRequest.Period.builder()
                .start(fromDate.format(DateTimeFormatter.ISO_LOCAL_DATE))
                .end(toDate.format(DateTimeFormatter.ISO_LOCAL_DATE))
                .build();
        
        return SaleFunnelHistoryRequest.builder()
                .selectedPeriod(period)
                .nmIds(List.of(nmId))
                .skipDeletedNm(false)
                .aggregationLevel("day")
                .build();
    }

    private SaleFunnelResponse parseAnalyticsResponse(ResponseEntity<String> response, Long nmId) {
        try {
            List<SaleFunnelHistoryResponse> historyResponses = objectMapper.readValue(
                    response.getBody(),
                    objectMapper.getTypeFactory().constructCollectionType(
                            List.class, 
                            SaleFunnelHistoryResponse.class
                    )
            );
            
            return convertToSaleFunnelResponse(historyResponses, nmId);
                    
        } catch (Exception e) {
            log.error("Ошибка при парсинге ответа аналитики для nmID {}: {}", nmId, e.getMessage());
            throw new RestClientException("Ошибка при парсинге ответа аналитики: " + e.getMessage(), e);
        }
    }

    private SaleFunnelResponse convertToSaleFunnelResponse(
            List<SaleFunnelHistoryResponse> historyResponses, 
            Long nmId
    ) {
        List<SaleFunnelResponse.DailyData> dailyDataList = new ArrayList<>();
        
        for (SaleFunnelHistoryResponse response : historyResponses) {
            if (response == null || response.getProduct() == null || response.getHistory() == null) {
                continue;
            }
            
            if (!nmId.equals(response.getProduct().getNmId())) {
                continue;
            }
            
            for (SaleFunnelHistoryResponse.HistoryItem item : response.getHistory()) {
                if (item == null || item.getDate() == null) {
                    continue;
                }
                
                SaleFunnelResponse.DailyData dailyData = SaleFunnelResponse.DailyData.builder()
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
        
        return SaleFunnelResponse.builder()
                .data(dailyDataList)
                .build();
    }
}

