package ru.oparin.solution.util;

import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientResponseException;
import ru.oparin.solution.exception.WbApiUnauthorizedScopeException;

import java.util.Locale;

/**
 * Распознавание 401 / отозванного токена WB для очереди событий.
 */
public final class WbTokenAuthErrors {

    public static final String INVALID_KEY_USER_MESSAGE =
            "API-токен WB отозван или невалиден. Обновите ключ в настройках кабинета.";

    private WbTokenAuthErrors() {
    }

    /**
     * Ошибка авторизации WB: ретраи бесполезны (401 / unauthorized / scope 401).
     */
    public static boolean isUnauthorizedNoRetry(Throwable throwable) {
        if (throwable == null) {
            return false;
        }
        if (findInChain(throwable, WbApiUnauthorizedScopeException.class) != null) {
            return true;
        }
        if (hasHttpStatus(throwable, 401)) {
            return true;
        }
        return isUnauthorizedMessage(collectMessages(throwable));
    }

    /**
     * Токен целиком мёртв (отозван / пустой 401), а не «нет одной категории».
     * В этом случае нужно пометить {@code is_valid=false} и снять очередь кабинета.
     */
    public static boolean isTokenFullyInvalid(Throwable throwable) {
        if (throwable == null) {
            return false;
        }
        String messages = collectMessages(throwable).toLowerCase(Locale.ROOT);
        if (isScopeOnlyDenied(messages)) {
            return false;
        }
        if (messages.contains("withdrawn")
                || messages.contains("token is invalid")
                || messages.contains("api ключ невалиден")
                || messages.contains("ключ отклонён")
                || messages.contains("[no body]")) {
            return true;
        }
        return hasHttpStatus(throwable, 401) && !isScopeOnlyDenied(messages);
    }

    /**
     * То же по тексту ошибки (уже сохранённому в событии).
     */
    public static boolean isTokenFullyInvalidMessage(String message) {
        if (message == null || message.isBlank()) {
            return false;
        }
        String lower = message.toLowerCase(Locale.ROOT);
        if (isScopeOnlyDenied(lower)) {
            return false;
        }
        return lower.contains("withdrawn")
                || lower.contains("token is invalid")
                || lower.contains("api-токен wb отозван")
                || lower.contains("api ключ невалиден")
                || lower.contains("ключ отклонён")
                || lower.contains("[no body]")
                || ((lower.contains("401") || lower.contains("unauthorized")) && !isScopeOnlyDenied(lower));
    }

    public static boolean isUnauthorizedMessage(String message) {
        if (message == null || message.isBlank()) {
            return false;
        }
        String lower = message.toLowerCase(Locale.ROOT);
        return lower.contains("401") || lower.contains("unauthorized");
    }

    private static boolean isScopeOnlyDenied(String lowerMessages) {
        return lowerMessages.contains("scope not allowed")
                || lowerMessages.contains("token scope")
                || lowerMessages.contains("не имеет доступа к категории");
    }

    private static boolean hasHttpStatus(Throwable throwable, int status) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof HttpStatusCodeException http
                    && http.getStatusCode().value() == status) {
                return true;
            }
            if (current instanceof RestClientResponseException http
                    && http.getStatusCode().value() == status) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private static <T extends Throwable> T findInChain(Throwable throwable, Class<T> type) {
        Throwable current = throwable;
        while (current != null) {
            if (type.isInstance(current)) {
                return type.cast(current);
            }
            current = current.getCause();
        }
        return null;
    }

    private static String collectMessages(Throwable throwable) {
        StringBuilder sb = new StringBuilder();
        Throwable current = throwable;
        while (current != null) {
            if (current.getMessage() != null) {
                if (!sb.isEmpty()) {
                    sb.append(' ');
                }
                sb.append(current.getMessage());
            }
            current = current.getCause();
        }
        return sb.toString();
    }
}
