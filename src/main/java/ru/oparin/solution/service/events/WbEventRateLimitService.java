package ru.oparin.solution.service.events;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import ru.oparin.solution.model.CabinetTokenType;
import ru.oparin.solution.model.WbApiEvent;
import ru.oparin.solution.model.WbApiEventType;

import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

@Service
@RequiredArgsConstructor
public class WbEventRateLimitService {

    @Value("${wb.content.cards-pagination-basic-ms}")
    private long contentDelayBasicMs;
    @Value("${wb.content.cards-pagination-personal-ms}")
    private long contentDelayPersonalMs;
    @Value("${wb.analytics.card-basic-ms}")
    private long analyticsDelayBasicMs;
    @Value("${wb.analytics.card-personal-ms}")
    private long analyticsDelayPersonalMs;
    @Value("${wb.prices.api-call-basic-ms}")
    private long pricesDelayBasicMs;
    @Value("${wb.prices.api-call-personal-ms}")
    private long pricesDelayPersonalMs;
    @Value("${wb.promotion.adverts-basic-ms}")
    private long promotionAdvertsDelayBasicMs;
    @Value("${wb.promotion.adverts-personal-ms}")
    private long promotionAdvertsDelayPersonalMs;
    @Value("${wb.promotion.statistics-basic-ms}")
    private long promotionStatisticsDelayBasicMs;
    @Value("${wb.promotion.statistics-personal-ms}")
    private long promotionStatisticsDelayPersonalMs;
    @Value("${wb.feedbacks.request-basic-ms}")
    private long feedbacksDelayBasicMs;
    @Value("${wb.feedbacks.request-personal-ms}")
    private long feedbacksDelayPersonalMs;
    @Value("${wb.stocks.request-basic-ms}")
    private long stocksDelayBasicMs;
    @Value("${wb.stocks.request-personal-ms}")
    private long stocksDelayPersonalMs;
    @Value("${wb.calendar.request-basic-ms}")
    private long calendarDelayBasicMs;
    @Value("${wb.calendar.request-personal-ms}")
    private long calendarDelayPersonalMs;
    @Value("${wb.warehouses.request-basic-ms}")
    private long warehousesDelayBasicMs;
    @Value("${wb.warehouses.request-personal-ms}")
    private long warehousesDelayPersonalMs;
    private final ConcurrentHashMap<String, LocalDateTime> lastCallByCabinetAndType = new ConcurrentHashMap<>();

    /**
     * Атомарно проверяет лимит по паре (cabinetId, eventType).
     *
     * @return null, если вызов можно выполнять сейчас; иначе время, когда вызов станет допустим.
     */
    public LocalDateTime acquireOrDefer(WbApiEvent event) {
        Long cabinetId = event.getCabinet() != null ? event.getCabinet().getId() : null;
        if (cabinetId == null) {
            return null;
        }

        CabinetTokenType tokenType = event.getCabinet().getTokenType() != null
                ? event.getCabinet().getTokenType()
                : CabinetTokenType.BASIC;
        int intervalSeconds = resolveRateLimitSeconds(event.getEventType(), tokenType);
        if (intervalSeconds <= 0) {
            return null;
        }

        String key = buildKey(event.getEventType(), cabinetId);
        LocalDateTime now = LocalDateTime.now();
        AtomicReference<LocalDateTime> deferUntilRef = new AtomicReference<>();

        lastCallByCabinetAndType.compute(key, (k, lastCallAt) -> {
            if (lastCallAt == null) {
                return now;
            }
            LocalDateTime allowedAt = lastCallAt.plusSeconds(intervalSeconds);
            if (!now.isBefore(allowedAt)) {
                return now;
            }
            deferUntilRef.set(allowedAt);
            return lastCallAt;
        });

        return deferUntilRef.get();
    }

    private int resolveRateLimitSeconds(WbApiEventType type, CabinetTokenType tokenType) {
        boolean personal = tokenType == CabinetTokenType.PERSONAL;
        long delayMs = switch (type) {
            case CONTENT_CARDS_LIST_PAGE -> personal ? contentDelayPersonalMs : contentDelayBasicMs;
            case ANALYTICS_SALES_FUNNEL_NMID -> personal ? analyticsDelayPersonalMs : analyticsDelayBasicMs;
            case PRICES_CABINET_WITH_SPP -> personal ? pricesDelayPersonalMs : pricesDelayBasicMs;
            case PROMOTION_COUNT, PROMOTION_ADVERTS_BATCH ->
                    personal ? promotionAdvertsDelayPersonalMs : promotionAdvertsDelayBasicMs;
            case PROMOTION_STATS_BATCH ->
                    personal ? promotionStatisticsDelayPersonalMs : promotionStatisticsDelayBasicMs;
            case FEEDBACKS_SYNC_CABINET -> personal ? feedbacksDelayPersonalMs : feedbacksDelayBasicMs;
            case STOCKS_BY_NMID -> personal ? stocksDelayPersonalMs : stocksDelayBasicMs;
            case PROMOTION_CALENDAR_SYNC_CABINET -> personal ? calendarDelayPersonalMs : calendarDelayBasicMs;
            case WAREHOUSES_SYNC_CABINET -> personal ? warehousesDelayPersonalMs : warehousesDelayBasicMs;
        };
        if (delayMs <= 0) {
            return 0;
        }
        return (int) Math.max(1, (delayMs + 999) / 1000);
    }

    private static String buildKey(WbApiEventType type, Long cabinetId) {
        return type.name() + ":" + cabinetId;
    }
}
