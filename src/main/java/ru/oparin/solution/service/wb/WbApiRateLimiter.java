package ru.oparin.solution.service.wb;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Глобальный in-memory лимитер WB API по категориям.
 * Ограничивает минимальный интервал между вызовами одной категории по всему приложению.
 */
@Component
public class WbApiRateLimiter {

    private static volatile WbApiRateLimiter INSTANCE;

    @Value("${wb.rate-limit.default-min-interval-ms:0}")
    private long defaultMinIntervalMs;
    @Value("${wb.rate-limit.analytics-min-interval-ms:200}")
    private long analyticsMinIntervalMs;
    @Value("${wb.rate-limit.promotion-min-interval-ms:200}")
    private long promotionMinIntervalMs;
    @Value("${wb.rate-limit.content-min-interval-ms:700}")
    private long contentMinIntervalMs;
    @Value("${wb.rate-limit.prices-min-interval-ms:600}")
    private long pricesMinIntervalMs;
    @Value("${wb.rate-limit.feedbacks-min-interval-ms:350}")
    private long feedbacksMinIntervalMs;
    @Value("${wb.rate-limit.statistics-min-interval-ms:200}")
    private long statisticsMinIntervalMs;

    private final ConcurrentMap<WbApiCategory, AtomicLong> nextAllowedAtMs = new ConcurrentHashMap<>();
    private final ConcurrentMap<WbApiCategory, AtomicLong> tooManyRequestsCounters = new ConcurrentHashMap<>();
    private final Map<WbApiCategory, Long> categoryIntervals = new EnumMap<>(WbApiCategory.class);

    @PostConstruct
    void init() {
        categoryIntervals.put(WbApiCategory.ANALYTICS, analyticsMinIntervalMs);
        categoryIntervals.put(WbApiCategory.PROMOTION, promotionMinIntervalMs);
        categoryIntervals.put(WbApiCategory.CONTENT, contentMinIntervalMs);
        categoryIntervals.put(WbApiCategory.PRICES_AND_DISCOUNTS, pricesMinIntervalMs);
        categoryIntervals.put(WbApiCategory.FEEDBACKS_AND_QUESTIONS, feedbacksMinIntervalMs);
        categoryIntervals.put(WbApiCategory.STATISTICS, statisticsMinIntervalMs);
        INSTANCE = this;
    }

    public static void acquire(WbApiCategory category) {
        WbApiRateLimiter limiter = INSTANCE;
        if (limiter == null || category == null) {
            return;
        }
        limiter.acquireInternal(category);
    }

    public static long increment429AndGet(WbApiCategory category) {
        WbApiRateLimiter limiter = INSTANCE;
        if (limiter == null || category == null) {
            return 0L;
        }
        return limiter.tooManyRequestsCounters
                .computeIfAbsent(category, ignored -> new AtomicLong())
                .incrementAndGet();
    }

    private void acquireInternal(WbApiCategory category) {
        long intervalMs = categoryIntervals.getOrDefault(category, defaultMinIntervalMs);
        if (intervalMs <= 0) {
            return;
        }

        AtomicLong nextAllowed = nextAllowedAtMs.computeIfAbsent(category, ignored -> new AtomicLong(0));

        while (true) {
            long now = System.currentTimeMillis();
            long currentNextAllowed = nextAllowed.get();
            if (currentNextAllowed <= now) {
                long newNextAllowed = now + intervalMs;
                if (nextAllowed.compareAndSet(currentNextAllowed, newNextAllowed)) {
                    return;
                }
                continue;
            }

            long sleepMs = currentNextAllowed - now;
            safeSleep(sleepMs);
        }
    }

    private void safeSleep(long sleepMs) {
        if (sleepMs <= 0) return;
        try {
            Thread.sleep(sleepMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
