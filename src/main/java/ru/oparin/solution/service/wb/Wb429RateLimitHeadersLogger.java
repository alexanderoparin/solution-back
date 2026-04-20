package ru.oparin.solution.service.wb;

import org.slf4j.Logger;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.HttpClientErrorException;

/**
 * Логирование заголовков rate-limit ответа WB API (см. документацию WB).
 */
public final class Wb429RateLimitHeadersLogger {

    public static final String X_RATELIMIT_REMAINING = "X-Ratelimit-Remaining";
    public static final String X_RATELIMIT_RETRY = "X-Ratelimit-Retry";
    public static final String X_RATELIMIT_LIMIT = "X-Ratelimit-Limit";
    public static final String X_RATELIMIT_RESET = "X-Ratelimit-Reset";

    private Wb429RateLimitHeadersLogger() {
    }

    /**
     * Пишет в лог значения заголовков лимита, если хотя бы один из них присутствует в ответе.
     */
    public static void logRateLimitHeaders(Logger log, String endpointKey, int httpStatus, HttpHeaders headers) {
        if (headers == null) {
            return;
        }
        String remaining = headers.getFirst(X_RATELIMIT_REMAINING);
        String limit = headers.getFirst(X_RATELIMIT_LIMIT);
        String reset = headers.getFirst(X_RATELIMIT_RESET);
        String retry = headers.getFirst(X_RATELIMIT_RETRY);
        if (isMissing(remaining) && isMissing(limit) && isMissing(reset) && isMissing(retry)) {
            return;
        }
        log.debug("WB API rate-limit headers: endpointKey={}, HTTP={}, {}={}, {}={}, {}={}, {}={}",
                endpointKey != null ? endpointKey : "",
                httpStatus,
                X_RATELIMIT_REMAINING, orDash(remaining),
                X_RATELIMIT_LIMIT, orDash(limit),
                X_RATELIMIT_RESET, orDash(reset),
                X_RATELIMIT_RETRY, orDash(retry));
    }

    private static boolean isMissing(String value) {
        return value == null || value.isBlank();
    }

    private static String orDash(String value) {
        return value != null && !value.isBlank() ? value : "—";
    }

    public static Integer parsePositiveIntHeader(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            long parsed = Long.parseLong(raw.trim());
            if (parsed < 0) {
                return null;
            }
            if (parsed > Integer.MAX_VALUE) {
                return Integer.MAX_VALUE;
            }
            return (int) parsed;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    /**
     * Значение заголовка {@value #X_RATELIMIT_RETRY} из ответа 429 (секунды до повтора), если есть и парсится.
     */
    public static Integer parseRetryAfterSeconds(HttpClientErrorException e) {
        if (e == null || e.getStatusCode().value() != 429 || e.getResponseHeaders() == null) {
            return null;
        }
        return parsePositiveIntHeader(e.getResponseHeaders().getFirst(X_RATELIMIT_RETRY));
    }
}
