package ru.oparin.solution.service.wb;

import org.springframework.stereotype.Component;
import ru.oparin.solution.model.CabinetTokenType;
import ru.oparin.solution.model.WbApiEventType;

import java.util.Arrays;
import java.util.Comparator;

/**
 * Интервал паузы после успешного (2xx) ответа WB по {@code endpointKey} (host+path) и категории.
 * Берёт значения из метаданных {@link WbApiEventType}.
 * Для путей без явного правила — минимальный технический интервал {@link #FALLBACK_UNKNOWN_PATH_MS}.
 */
@Component
public class WbHttpSuccessSpacingMsResolver {

    /** Если путь не сопоставлен с известными префиксами WB в этом резолвере. */
    private static final long FALLBACK_UNKNOWN_PATH_MS = 1001L;

    /**
     * Минимальная пауза до следующего запроса к тому же endpoint с тем же токеном после 2xx (мс).
     */
    public long spacingAfter2xxMs(String endpointKey, CabinetTokenType tokenType) {
        if (endpointKey == null || endpointKey.isBlank()) {
            return positiveMs(FALLBACK_UNKNOWN_PATH_MS);
        }
        // Сначала более длинные URI, иначе «.../promotions» перехватит «.../promotions/nomenclatures».
        WbApiEventType[] types = WbApiEventType.values();
        Arrays.sort(types, Comparator.comparingInt((WbApiEventType t) -> t.getUri().length()).reversed());
        for (WbApiEventType type : types) {
            if (endpointKey.contains(type.getUri())) {
                return positiveMs(type.getRequestDelayMs(tokenType));
            }
        }
        return positiveMs(FALLBACK_UNKNOWN_PATH_MS);
    }

    private static long positiveMs(long configured) {
        return Math.max(1L, configured);
    }
}
