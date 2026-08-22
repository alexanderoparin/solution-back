package ru.oparin.solution.exception;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Objects;

/**
 * Нельзя выполнить запрос к Ozon сейчас из‑за лимита: нужно отложить событие, поток не блокировать.
 */
public final class OzonRateLimitDeferException extends RuntimeException {

    private final LocalDateTime deferUntil;

    public OzonRateLimitDeferException(String message, LocalDateTime deferUntil) {
        super(message);
        this.deferUntil = Objects.requireNonNull(deferUntil, "deferUntil");
    }

    public static OzonRateLimitDeferException untilEpochMilli(String message, long epochMilliUtc) {
        LocalDateTime until = LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMilliUtc), ZoneId.systemDefault());
        return new OzonRateLimitDeferException(message, until);
    }

    public LocalDateTime getDeferUntil() {
        return deferUntil;
    }

    public static OzonRateLimitDeferException findInChain(Throwable t) {
        for (Throwable c = t; c != null; c = c.getCause()) {
            if (c instanceof OzonRateLimitDeferException d) {
                return d;
            }
        }
        return null;
    }
}
