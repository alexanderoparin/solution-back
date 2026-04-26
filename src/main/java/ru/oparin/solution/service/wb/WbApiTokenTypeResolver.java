package ru.oparin.solution.service.wb;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.oparin.solution.model.Cabinet;
import ru.oparin.solution.model.CabinetTokenType;
import ru.oparin.solution.repository.CabinetRepository;

import java.util.Locale;

/**
 * Определяет тип токена кабинета по API-ключу.
 */
@Component
@RequiredArgsConstructor
public class WbApiTokenTypeResolver {

    private static final String BEARER_PREFIX = "bearer ";

    private final CabinetRepository cabinetRepository;

    public CabinetTokenType resolveByAuthorizationHeader(String authorizationHeader) {
        return resolveByApiKey(extractApiKey(authorizationHeader));
    }

    public CabinetTokenType resolveByApiKey(String apiKey) {
        String normalized = normalizeApiKey(apiKey);
        if (normalized == null) {
            return CabinetTokenType.BASIC;
        }
        return cabinetRepository.findTopByApiKeyOrderByIdDesc(normalized)
                .map(Cabinet::getTokenType)
                .orElse(CabinetTokenType.BASIC);
    }

    private String extractApiKey(String authorizationHeader) {
        String normalized = normalizeApiKey(authorizationHeader);
        if (normalized == null) {
            return null;
        }
        String lower = normalized.toLowerCase(Locale.ROOT);
        if (lower.startsWith(BEARER_PREFIX)) {
            String raw = normalized.substring(BEARER_PREFIX.length()).trim();
            return raw.isEmpty() ? null : raw;
        }
        return normalized;
    }

    private String normalizeApiKey(String apiKey) {
        if (apiKey == null) {
            return null;
        }
        String trimmed = apiKey.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
