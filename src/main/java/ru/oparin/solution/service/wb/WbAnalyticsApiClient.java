package ru.oparin.solution.service.wb;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
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
        
        LocalDate validatedFromDate = validateAndAdjustDateFrom(dateFrom);
        LocalDate validatedToDate = validateAndAdjustDateTo(dateTo);
        
        SaleFunnelHistoryRequest request = buildAnalyticsRequest(nmId, validatedFromDate, validatedToDate);
        
        ResponseEntity<String> response = executeWithRetry(
                fullUrl, 
                apiKey, 
                request, 
                MAX_RETRIES_429, 
                RETRY_DELAY_MS_429
        );
        
        return parseAnalyticsResponse(response, nmId);
    }

    private LocalDate validateAndAdjustDateFrom(String dateFrom) {
        LocalDate fromDate = LocalDate.parse(dateFrom);
        LocalDate today = LocalDate.now();
        LocalDate minDate = today.minusDays(MAX_ANALYTICS_PERIOD_DAYS);
        
        if (fromDate.isBefore(minDate)) {
            log.warn("Дата начала {} слишком старая, ограничиваем до {}", fromDate, minDate);
            return minDate;
        }
        
        return fromDate;
    }

    private LocalDate validateAndAdjustDateTo(String dateTo) {
        LocalDate toDate = LocalDate.parse(dateTo);
        LocalDate today = LocalDate.now();
        
        if (toDate.isAfter(today)) {
            log.warn("Дата окончания {} в будущем, ограничиваем до {}", toDate, today);
            return today;
        }
        
        return toDate;
    }

    private SaleFunnelHistoryRequest buildAnalyticsRequest(Long nmId, LocalDate fromDate, LocalDate toDate) {
        long daysBetween = ChronoUnit.DAYS.between(fromDate, toDate);
        
        if (daysBetween > MAX_ANALYTICS_PERIOD_DAYS) {
            log.warn("Период превышает {} дней ({} дней), ограничиваем дату начала", 
                    MAX_ANALYTICS_PERIOD_DAYS, daysBetween);
            fromDate = toDate.minusDays(MAX_ANALYTICS_PERIOD_DAYS);
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

