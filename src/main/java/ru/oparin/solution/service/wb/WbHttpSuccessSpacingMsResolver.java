package ru.oparin.solution.service.wb;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Интервал паузы после успешного (2xx) ответа WB по {@code endpointKey} (host+path) и категории.
 * Берёт значения из блока {@code wb.*} «WB sync delays / retries» в {@code application.yaml},
 * в одном ряду с {@link ru.oparin.solution.service.events.WbEventRateLimitService}.
 * Для путей без явного правила — минимальный технический интервал {@link #FALLBACK_UNKNOWN_PATH_MS}.
 */
@Component
public class WbHttpSuccessSpacingMsResolver {

    /** Если путь не сопоставлен с известными префиксами WB в этом резолвере. */
    private static final long FALLBACK_UNKNOWN_PATH_MS = 1L;

    @Value("${wb.stocks.request-delay-ms:20000}")
    private long stocksRequestDelayMs;
    @Value("${wb.analytics.card-delay-ms:20000}")
    private long analyticsCardDelayMs;
    @Value("${wb.content.cards-pagination-delay-ms:700}")
    private long contentCardsPaginationDelayMs;
    @Value("${wb.prices.api-call-delay-ms:600}")
    private long pricesApiCallDelayMs;
    @Value("${wb.promotion.adverts-delay-ms:200}")
    private long promotionAdvertsDelayMs;
    @Value("${wb.promotion.statistics-delay-ms:20000}")
    private long promotionStatisticsDelayMs;
    @Value("${wb.feedbacks.request-delay-ms:350}")
    private long feedbacksRequestDelayMs;
    @Value("${wb.calendar.request-delay-ms:1000}")
    private long calendarRequestDelayMs;
    @Value("${wb.warehouses.request-delay-ms:1000}")
    private long warehousesRequestDelayMs;

    /**
     * Минимальная пауза до следующего запроса к тому же endpoint с тем же токеном после 2xx (мс).
     */
    public long spacingAfter2xxMs(String endpointKey) {
        if (endpointKey == null || endpointKey.isBlank()) {
            return positiveMs(FALLBACK_UNKNOWN_PATH_MS);
        }
        if (endpointKey.contains("/stocks-report/")) {
            return positiveMs(stocksRequestDelayMs);
        }
        if (endpointKey.contains("/sales-funnel/")) {
            return positiveMs(analyticsCardDelayMs);
        }
        if (endpointKey.contains("/content/v2/get/cards/")) {
            return positiveMs(contentCardsPaginationDelayMs);
        }
        if (endpointKey.contains("/api/v2/list/goods/filter")) {
            return positiveMs(pricesApiCallDelayMs);
        }
        if (endpointKey.contains("/adv/v3/fullstats")) {
            return positiveMs(promotionStatisticsDelayMs);
        }
        if (endpointKey.contains("/api/advert/v2/adverts") || endpointKey.contains("/adv/v1/promotion/count")) {
            return positiveMs(promotionAdvertsDelayMs);
        }
        if (endpointKey.contains("/api/v1/feedbacks")) {
            return positiveMs(feedbacksRequestDelayMs);
        }
        if (endpointKey.contains("/api/v1/calendar/")) {
            return positiveMs(calendarRequestDelayMs);
        }
        if (endpointKey.contains("/api/v1/warehouses")) {
            return positiveMs(warehousesRequestDelayMs);
        }
        return positiveMs(FALLBACK_UNKNOWN_PATH_MS);
    }

    private static long positiveMs(long configured) {
        return Math.max(1L, configured);
    }
}
