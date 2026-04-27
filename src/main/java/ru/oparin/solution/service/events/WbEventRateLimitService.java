package ru.oparin.solution.service.events;

import lombok.RequiredArgsConstructor;
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
        long delayMs = type.getRequestDelayMs(tokenType);
        if (delayMs <= 0) {
            return 0;
        }
        return (int) Math.max(1, (delayMs + 999) / 1000);
    }

    private static String buildKey(WbApiEventType type, Long cabinetId) {
        return type.name() + ":" + cabinetId;
    }
}
