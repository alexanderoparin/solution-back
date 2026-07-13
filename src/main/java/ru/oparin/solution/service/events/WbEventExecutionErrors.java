package ru.oparin.solution.service.events;

import org.springframework.web.client.RestClientException;
import ru.oparin.solution.exception.WbRateLimitDeferException;

/**
 * Преобразование исключений из WB-вызовов в результат выполнения события.
 */
public final class WbEventExecutionErrors {

    private static final String ENDPOINT_SLOT_DEFER_PREFIX = "Лимит WB по endpoint (токен+path)";

    private WbEventExecutionErrors() {
    }

    /**
     * Если в цепочке есть {@link WbRateLimitDeferException} — отложить событие; иначе {@code null}.
     */
    public static WbApiEventExecutionResult deferResultIfPresent(Throwable throwable) {
        WbRateLimitDeferException defer = WbRateLimitDeferException.findInChain(throwable);
        if (defer != null) {
            return fromDeferException(defer);
        }
        return null;
    }

    /**
     * Обрабатывает {@link RestClientException}: defer в цепочке, иначе retryable.
     */
    public static WbApiEventExecutionResult wrapRestClientException(RestClientException exception) {
        WbApiEventExecutionResult deferResult = deferResultIfPresent(exception);
        if (deferResult != null) {
            return deferResult;
        }
        return WbApiEventExecutionResult.retryableError(exception.getMessage());
    }

    /**
     * Если в цепочке есть {@link WbRateLimitDeferException} — отложить событие; иначе retryable.
     */
    public static WbApiEventExecutionResult wrapDeferOrRetryable(Throwable throwable) {
        WbApiEventExecutionResult deferResult = deferResultIfPresent(throwable);
        if (deferResult != null) {
            return deferResult;
        }
        return WbApiEventExecutionResult.retryableError(throwable.getMessage());
    }

    /**
     * Преобразует defer-исключение: ожидание слота endpoint — без попытки; после HTTP-ошибки — с попыткой.
     */
    public static WbApiEventExecutionResult fromDeferException(WbRateLimitDeferException defer) {
        if (isEndpointSlotDefer(defer.getMessage())) {
            return WbApiEventExecutionResult.deferredScheduling(defer.getMessage(), defer.getDeferUntil());
        }
        return WbApiEventExecutionResult.deferredRateLimit(defer.getMessage(), defer.getDeferUntil());
    }

    private static boolean isEndpointSlotDefer(String message) {
        return message != null && message.startsWith(ENDPOINT_SLOT_DEFER_PREFIX);
    }
}
