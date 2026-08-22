package ru.oparin.solution.service.ozon;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import ru.oparin.solution.exception.OzonRateLimitDeferException;

import java.net.URI;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Простой учёт лимитов Ozon по паре «Client-Id + endpoint».
 * После 2xx — минимальный spacing; на 429 — пауза по Retry-After или fallback.
 * Короткие ожидания — sleep в потоке; длинные — {@link OzonRateLimitDeferException}.
 */
@Component
@Slf4j
public class OzonEndpointRateLimitCoordinator {

    private static final long SHORT_SLOT_WAIT_BUDGET_MS = 5_000L;
    private static final long DEFAULT_429_BACKOFF_MS = 60_000L;

    private final long successSpacingMs;
    private final ConcurrentMap<String, RateSlot> slots = new ConcurrentHashMap<>();

    public OzonEndpointRateLimitCoordinator(
            @Value("${app.ozon-events.success-spacing-ms:250}") long successSpacingMs
    ) {
        this.successSpacingMs = Math.max(0L, successSpacingMs);
    }

    /**
     * Ключ endpoint: host + path (без query).
     */
    public static String endpointKeyFromUrl(String url) {
        if (url == null || url.isBlank()) {
            return "";
        }
        try {
            URI u = URI.create(url.trim());
            String host = u.getHost() != null ? u.getHost().toLowerCase(Locale.ROOT) : "";
            String path = u.getPath();
            if (path == null || path.isBlank()) {
                path = "/";
            }
            return host + path;
        } catch (IllegalArgumentException e) {
            return url;
        }
    }

    /**
     * Ждёт слот или откладывает событие при длинной паузе.
     */
    public void beforeRequest(String clientId, String endpointKey) {
        if (clientId == null || clientId.isBlank() || endpointKey == null || endpointKey.isBlank()) {
            return;
        }
        RateSlot slot = slots.computeIfAbsent(slotKey(clientId, endpointKey), k -> new RateSlot());
        long shortWaitBudgetUntil = System.currentTimeMillis() + SHORT_SLOT_WAIT_BUDGET_MS;

        while (true) {
            long until = slot.getNextAllowedAtMs();
            long now = System.currentTimeMillis();
            if (until <= now) {
                return;
            }
            long waitMs = until - now;
            if (now >= shortWaitBudgetUntil || waitMs > SHORT_SLOT_WAIT_BUDGET_MS) {
                log.debug("Ozon endpoint defer: endpointKey={}, waitMs={}", endpointKey, waitMs);
                throw OzonRateLimitDeferException.untilEpochMilli(
                        "Лимит Ozon API: следующий запрос не раньше указанного времени.",
                        until
                );
            }
            long sleepMs = Math.max(1L, Math.min(waitMs, shortWaitBudgetUntil - now));
            log.debug("Ozon endpoint short slot wait: endpointKey={}, sleepMs={}", endpointKey, sleepMs);
            try {
                Thread.sleep(sleepMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw OzonRateLimitDeferException.untilEpochMilli(
                        "Ожидание лимита Ozon прервано",
                        until
                );
            }
        }
    }

    /**
     * Обновляет слот после ответа HTTP.
     */
    public void afterResponse(String clientId, String endpointKey, int httpStatus, HttpHeaders headers) {
        if (clientId == null || clientId.isBlank() || endpointKey == null || endpointKey.isBlank()) {
            return;
        }
        RateSlot slot = slots.computeIfAbsent(slotKey(clientId, endpointKey), k -> new RateSlot());
        long now = System.currentTimeMillis();
        if (httpStatus == 429) {
            long backoffMs = parseRetryAfterMs(headers).orElse(DEFAULT_429_BACKOFF_MS);
            slot.setNextAllowedAtMs(now + Math.max(1_000L, backoffMs));
            return;
        }
        if (httpStatus >= 200 && httpStatus < 300 && successSpacingMs > 0) {
            slot.setNextAllowedAtMs(Math.max(slot.getNextAllowedAtMs(), now + successSpacingMs));
        }
    }

    private static java.util.OptionalLong parseRetryAfterMs(HttpHeaders headers) {
        if (headers == null) {
            return java.util.OptionalLong.empty();
        }
        String retryAfter = headers.getFirst(HttpHeaders.RETRY_AFTER);
        if (retryAfter == null || retryAfter.isBlank()) {
            return java.util.OptionalLong.empty();
        }
        try {
            long seconds = Long.parseLong(retryAfter.trim());
            if (seconds > 0) {
                return java.util.OptionalLong.of(seconds * 1_000L);
            }
        } catch (NumberFormatException ignored) {
            // Retry-After может быть HTTP-date — игнорируем, используем fallback
        }
        return java.util.OptionalLong.empty();
    }

    private static String slotKey(String clientId, String endpointKey) {
        return clientId.trim() + "|" + endpointKey;
    }

    private static final class RateSlot {
        private final AtomicLong nextAllowedAtMs = new AtomicLong(0);

        long getNextAllowedAtMs() {
            return nextAllowedAtMs.get();
        }

        void setNextAllowedAtMs(long value) {
            nextAllowedAtMs.updateAndGet(prev -> Math.max(prev, value));
        }
    }
}
