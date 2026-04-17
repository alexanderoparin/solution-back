package ru.oparin.solution.service.wb;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import ru.oparin.solution.exception.WbRateLimitDeferException;

import java.net.URI;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Учёт лимитов WB по паре «токен (отпечаток) + endpoint».
 * <p>
 * Цель — не доводить до 429 (в т.ч. из‑за возможных штрафных лимитов WB). На каждом успешном ответе
 * {@code 2xx} для слота выставляется «не раньше чем» {@code now +} пауза из блока {@code wb.*} sync delays
 * ({@link WbHttpSuccessSpacingMsResolver}), по тем же величинам, что и {@code WbEventRateLimitService}.
 * Для неизвестных путей — минимальный fallback в {@link WbHttpSuccessSpacingMsResolver}.
 * На {@code 429} — {@code X-Ratelimit-Retry} / {@code X-Ratelimit-Reset}, иначе пауза как после 2xx.
 * <p>
 * Время «не раньше чем» для следующего запроса по слоту — в {@link #slots} ({@code nextAllowedAtMs}).
 * При необходимости ожидания выбрасывается {@link ru.oparin.solution.exception.WbRateLimitDeferException} (события / retry снаружи).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class WbEndpointRateLimitCoordinator {

    private final WbHttpSuccessSpacingMsResolver httpSuccessSpacingMs;

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
            if (log.isDebugEnabled()) {
                log.debug("WB endpoint slot defer: category={}, endpointKey={}, deferUntilEpochMs={}",
                        category != null ? category.name() : "?", endpointKey, until);
            }
            throw WbRateLimitDeferException.untilEpochMilli(
                    "Лимит WB по endpoint (токен+path): следующий запрос не раньше указанного времени.",
                    until
            );
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
                slot.setNextAllowedAtMs(now + httpSuccessSpacingMs.spacingAfter2xxMs(endpointKey));
            }
        } else if (httpStatus >= 200 && httpStatus <= 299) {
            slot.setNextAllowedAtMs(now + httpSuccessSpacingMs.spacingAfter2xxMs(endpointKey));
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
