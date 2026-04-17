package ru.oparin.solution.service.events;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import ru.oparin.solution.model.WbApiEvent;
import ru.oparin.solution.model.WbApiEventType;

import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

@Service
@RequiredArgsConstructor
public class WbEventRateLimitService {

    @Value("${wb.content.cards-pagination-delay-ms}")
    private long contentDelayMs;
    @Value("${wb.analytics.card-delay-ms}")
    private long analyticsDelayMs;
    @Value("${wb.prices.api-call-delay-ms}")
    private long pricesDelayMs;
    @Value("${wb.promotion.adverts-delay-ms}")
    private long promotionAdvertsDelayMs;
    @Value("${wb.promotion.statistics-delay-ms}")
    private long promotionStatisticsDelayMs;
    @Value("${wb.feedbacks.request-delay-ms}")
    private long feedbacksDelayMs;
    @Value("${wb.stocks.request-delay-ms}")
    private long stocksDelayMs;
    @Value("${wb.calendar.request-delay-ms}")
    private long calendarDelayMs;
    @Value("${wb.warehouses.request-delay-ms}")
    private long warehousesDelayMs;
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

        int intervalSeconds = resolveRateLimitSeconds(event.getEventType());
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

    private int resolveRateLimitSeconds(WbApiEventType type) {
        long delayMs = switch (type) {
            case CONTENT_CARDS_LIST_PAGE -> contentDelayMs;
            case ANALYTICS_SALES_FUNNEL_NMID -> analyticsDelayMs;
            case PRICES_CABINET_WITH_SPP -> pricesDelayMs;
            case PROMOTION_COUNT, PROMOTION_ADVERTS_BATCH -> promotionAdvertsDelayMs;
            case PROMOTION_STATS_BATCH -> promotionStatisticsDelayMs;
            case FEEDBACKS_SYNC_CABINET -> feedbacksDelayMs;
            case STOCKS_BY_NMID -> stocksDelayMs;
            case PROMOTION_CALENDAR_SYNC_CABINET -> calendarDelayMs;
            case WAREHOUSES_SYNC_CABINET -> warehousesDelayMs;
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
