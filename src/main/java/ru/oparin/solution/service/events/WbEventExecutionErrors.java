package ru.oparin.solution.service.events;

import ru.oparin.solution.exception.WbRateLimitDeferException;

/**
 * Преобразование исключений из WB-вызовов в результат выполнения события.
 */
public final class WbEventExecutionErrors {

    private static final String ENDPOINT_SLOT_DEFER_PREFIX = "Лимит WB по endpoint (токен+path)";

    private WbEventExecutionErrors() {
    }

    /**
     * Если в цепочке есть {@link WbRateLimitDeferException} — отложить событие; иначе retryable.
     */
    public static WbApiEventExecutionResult wrapDeferOrRetryable(Throwable e) {
        WbRateLimitDeferException defer = WbRateLimitDeferException.findInChain(e);
        if (defer != null) {
            return fromDeferException(defer);
        }
        return WbApiEventExecutionResult.retryableError(e.getMessage());
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
