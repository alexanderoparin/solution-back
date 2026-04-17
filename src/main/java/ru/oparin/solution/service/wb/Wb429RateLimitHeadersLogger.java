package ru.oparin.solution.service.wb;

import org.slf4j.Logger;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.HttpClientErrorException;

/**
 * Логирование заголовков ответа WB API при 429 (см. документацию rate limit).
 */
public final class Wb429RateLimitHeadersLogger {

    public static final String X_RATELIMIT_RETRY = "X-Ratelimit-Retry";
    public static final String X_RATELIMIT_LIMIT = "X-Ratelimit-Limit";
    public static final String X_RATELIMIT_RESET = "X-Ratelimit-Reset";

    private Wb429RateLimitHeadersLogger() {
    }

    /**
     * Если {@code statusCode} — 429, пишет в лог значения заголовков (при отсутствии — «—»).
     */
    public static void logIf429(Logger log, HttpStatusCode statusCode, HttpHeaders headers) {
        if (statusCode == null || statusCode.value() != 429) {
            return;
        }
        if (headers == null) {
            log.warn("WB API 429: заголовки rate-limit отсутствуют (response headers null)");
            return;
        }
        log.warn("WB API 429 rate-limit: {}={}, {}={}, {}={}",
                X_RATELIMIT_RETRY, orDash(headers.getFirst(X_RATELIMIT_RETRY)),
                X_RATELIMIT_LIMIT, orDash(headers.getFirst(X_RATELIMIT_LIMIT)),
                X_RATELIMIT_RESET, orDash(headers.getFirst(X_RATELIMIT_RESET)));
    }

    public static void logIf429(Logger log, HttpClientErrorException e) {
        if (e == null) {
            return;
        }
        logIf429(log, e.getStatusCode(), e.getResponseHeaders());
    }

    private static String orDash(String value) {
        return value != null && !value.isBlank() ? value : "—";
    }

    /**
     * Значение заголовка {@value #X_RATELIMIT_RETRY} из ответа 429 (секунды до повтора), если есть и парсится.
     */
    public static Integer parseRetryAfterSeconds(HttpClientErrorException e) {
        if (e == null || e.getStatusCode().value() != 429 || e.getResponseHeaders() == null) {
            return null;
        }
        String raw = e.getResponseHeaders().getFirst(X_RATELIMIT_RETRY);
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
}
