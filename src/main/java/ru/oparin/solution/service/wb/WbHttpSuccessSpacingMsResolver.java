package ru.oparin.solution.service.wb;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import ru.oparin.solution.model.CabinetTokenType;

/**
 * Интервал паузы после успешного (2xx) ответа WB по {@code endpointKey} (host+path) и категории.
 * Берёт значения из блока {@code wb.*} «WB sync delays / retries» в {@code application.yaml},
 * в одном ряду с {@link ru.oparin.solution.service.events.WbEventRateLimitService}.
 * Для путей без явного правила — минимальный технический интервал {@link #FALLBACK_UNKNOWN_PATH_MS}.
 */
@Component
public class WbHttpSuccessSpacingMsResolver {

    /** Если путь не сопоставлен с известными префиксами WB в этом резолвере. */
    private static final long FALLBACK_UNKNOWN_PATH_MS = 1001L;

    @Value("${wb.stocks.request-basic-ms}")
    private long stocksRequestBasicMs;
    @Value("${wb.stocks.request-personal-ms}")
    private long stocksRequestPersonalMs;
    @Value("${wb.analytics.card-basic-ms}")
    private long analyticsCardBasicMs;
    @Value("${wb.analytics.card-personal-ms}")
    private long analyticsCardPersonalMs;
    @Value("${wb.content.cards-pagination-basic-ms}")
    private long contentCardsPaginationBasicMs;
    @Value("${wb.content.cards-pagination-personal-ms}")
    private long contentCardsPaginationPersonalMs;
    @Value("${wb.prices.api-call-basic-ms}")
    private long pricesApiCallBasicMs;
    @Value("${wb.prices.api-call-personal-ms}")
    private long pricesApiCallPersonalMs;
    @Value("${wb.promotion.adverts-basic-ms}")
    private long promotionAdvertsBasicMs;
    @Value("${wb.promotion.adverts-personal-ms}")
    private long promotionAdvertsPersonalMs;
    @Value("${wb.promotion.statistics-basic-ms}")
    private long promotionStatisticsBasicMs;
    @Value("${wb.promotion.statistics-personal-ms}")
    private long promotionStatisticsPersonalMs;
    @Value("${wb.feedbacks.request-basic-ms}")
    private long feedbacksRequestBasicMs;
    @Value("${wb.feedbacks.request-personal-ms}")
    private long feedbacksRequestPersonalMs;
    @Value("${wb.calendar.request-basic-ms}")
    private long calendarRequestBasicMs;
    @Value("${wb.calendar.request-personal-ms}")
    private long calendarRequestPersonalMs;
    @Value("${wb.warehouses.request-basic-ms}")
    private long warehousesRequestBasicMs;
    @Value("${wb.warehouses.request-personal-ms}")
    private long warehousesRequestPersonalMs;
    @Value("${wb.orders.request-basic-ms}")
    private long ordersRequestBasicMs;
    @Value("${wb.orders.request-personal-ms}")
    private long ordersRequestPersonalMs;

    /**
     * Минимальная пауза до следующего запроса к тому же endpoint с тем же токеном после 2xx (мс).
     */
    public long spacingAfter2xxMs(String endpointKey, CabinetTokenType tokenType) {
        boolean personal = tokenType == CabinetTokenType.PERSONAL;
        if (endpointKey == null || endpointKey.isBlank()) {
            return positiveMs(FALLBACK_UNKNOWN_PATH_MS);
        }
        if (endpointKey.contains("/stocks-report/")) {
            return positiveMs(personal ? stocksRequestPersonalMs : stocksRequestBasicMs);
        }
        if (endpointKey.contains("/sales-funnel/")) {
            return positiveMs(personal ? analyticsCardPersonalMs : analyticsCardBasicMs);
        }
        if (endpointKey.contains("/content/v2/get/cards/")) {
            return positiveMs(personal ? contentCardsPaginationPersonalMs : contentCardsPaginationBasicMs);
        }
        if (endpointKey.contains("/api/v2/list/goods/filter")) {
            return positiveMs(personal ? pricesApiCallPersonalMs : pricesApiCallBasicMs);
        }
        if (endpointKey.contains("/adv/v3/fullstats")) {
            return positiveMs(personal ? promotionStatisticsPersonalMs : promotionStatisticsBasicMs);
        }
        if (endpointKey.contains("/api/advert/v2/adverts") || endpointKey.contains("/adv/v1/promotion/count")) {
            return positiveMs(personal ? promotionAdvertsPersonalMs : promotionAdvertsBasicMs);
        }
        if (endpointKey.contains("/api/v1/feedbacks")) {
            return positiveMs(personal ? feedbacksRequestPersonalMs : feedbacksRequestBasicMs);
        }
        if (endpointKey.contains("/api/v1/calendar/")) {
            return positiveMs(personal ? calendarRequestPersonalMs : calendarRequestBasicMs);
        }
        if (endpointKey.contains("/api/v1/warehouses")) {
            return positiveMs(personal ? warehousesRequestPersonalMs : warehousesRequestBasicMs);
        }
        if (endpointKey.contains("/api/v1/supplier/orders")) {
            return positiveMs(personal ? ordersRequestPersonalMs : ordersRequestBasicMs);
        }
        return positiveMs(FALLBACK_UNKNOWN_PATH_MS);
    }

    private static long positiveMs(long configured) {
        return Math.max(1L, configured);
    }
}
