package ru.oparin.solution.exception;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Objects;

/**
 * Нельзя выполнить запрос к WB сейчас из‑за лимита: нужно отложить (событие / повтор позже), поток не блокировать.
 */
public final class WbRateLimitDeferException extends RuntimeException {

    private final LocalDateTime deferUntil;

    public WbRateLimitDeferException(String message, LocalDateTime deferUntil) {
        super(message);
        this.deferUntil = Objects.requireNonNull(deferUntil, "deferUntil");
    }

    public static WbRateLimitDeferException untilEpochMilli(String message, long epochMilliUtc) {
        LocalDateTime until = LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMilliUtc), ZoneId.systemDefault());
        return new WbRateLimitDeferException(message, until);
    }

    public LocalDateTime getDeferUntil() {
        return deferUntil;
    }

    public static WbRateLimitDeferException findInChain(Throwable t) {
        for (Throwable c = t; c != null; c = c.getCause()) {
            if (c instanceof WbRateLimitDeferException d) {
                return d;
            }
        }
        return null;
    }
}
