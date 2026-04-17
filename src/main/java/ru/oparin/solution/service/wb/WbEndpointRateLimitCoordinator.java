package ru.oparin.solution.service.wb;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Учёт лимитов WB по паре «токен (отпечаток) + endpoint».
 * <p>
 * Цель — не доводить до 429 (в т.ч. из‑за возможных штрафных лимитов WB). На каждом успешном ответе
 * {@code 2xx} для этого слота выставляется «не раньше чем» {@code now +} интервал категории из конфига
 * ({@link WbApiRateLimiter#minIntervalMsForCategory(WbApiCategory)}), чтобы следующий запрос к тому же
 * endpoint с тем же токеном не ушёл раньше заданного в {@code wb.rate-limit.*} шага.
 * На {@code 429} дополнительно учитываются заголовки {@code X-Ratelimit-Retry} и {@code X-Ratelimit-Reset}
 * (если есть), иначе — тот же конфиговый интервал.
 * <p>
 * Время «не раньше чем» для следующего запроса по слоту — в {@link #slots} ({@code nextAllowedAtMs}).
 * Глобальная пауза по категории для всех эндпоинтов — {@link WbApiRateLimiter#acquire(WbApiCategory)}.
 */
@Component
@Slf4j
public class WbEndpointRateLimitCoordinator {

    private final ConcurrentMap<String, RateSlot> slots = new ConcurrentHashMap<>();

    /**
     * Ключ для лимита: host + path (без query), нижний регистр host — чтобы не смешивать разные домены WB с одинаковым path.
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

    public void beforeRequest(String apiKey, String endpointKey, WbApiCategory category) {
        if (apiKey == null || apiKey.isBlank() || endpointKey == null || endpointKey.isBlank()) {
            return;
        }
        RateSlot slot = slots.computeIfAbsent(slotKey(apiKey, endpointKey), k -> new RateSlot());
        long until = slot.getNextAllowedAtMs();
        long now = System.currentTimeMillis();
        if (until > now) {
            long sleepMs = until - now;
            if (log.isDebugEnabled()) {
                log.debug("WB rate-limit wait: category={}, endpointKey={}, sleepMs={}",
                        category != null ? category.name() : "?", endpointKey, sleepMs);
            }
            try {
                Thread.sleep(sleepMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    public void afterResponse(String apiKey, String endpointKey, int httpStatus, HttpHeaders headers, WbApiCategory category) {
        if (apiKey == null || apiKey.isBlank() || endpointKey == null || endpointKey.isBlank() || headers == null) {
            return;
        }
        RateSlot slot = slots.computeIfAbsent(slotKey(apiKey, endpointKey), k -> new RateSlot());

        long now = System.currentTimeMillis();
        if (httpStatus == HttpStatus.TOO_MANY_REQUESTS.value()) {
            Integer retrySec = Wb429RateLimitHeadersLogger.parsePositiveIntHeader(
                    headers.getFirst(Wb429RateLimitHeadersLogger.X_RATELIMIT_RETRY));
            Integer resetSec = Wb429RateLimitHeadersLogger.parsePositiveIntHeader(
                    headers.getFirst(Wb429RateLimitHeadersLogger.X_RATELIMIT_RESET));
            if (retrySec != null && retrySec > 0) {
                slot.setNextAllowedAtMs(now + retrySec * 1000L);
            } else if (resetSec != null && resetSec > 0) {
                slot.setNextAllowedAtMs(now + resetSec * 1000L);
            } else {
                slot.setNextAllowedAtMs(now + WbApiRateLimiter.minIntervalMsForCategory(category));
            }
        } else if (httpStatus >= 200 && httpStatus <= 299) {
            long intervalMs = WbApiRateLimiter.minIntervalMsForCategory(category);
            slot.setNextAllowedAtMs(now + intervalMs);
        }
    }

    private static String slotKey(String apiKey, String endpointKey) {
        return Integer.toHexString(apiKey.trim().hashCode()) + "|" + endpointKey;
    }

    private static final class RateSlot {
        private final AtomicLong nextAllowedAtMs = new AtomicLong(0L);

        long getNextAllowedAtMs() {
            return nextAllowedAtMs.get();
        }

        void setNextAllowedAtMs(long epochMs) {
            nextAllowedAtMs.updateAndGet(prev -> Math.max(prev, epochMs));
        }
    }
}
