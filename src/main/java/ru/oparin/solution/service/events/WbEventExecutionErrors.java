package ru.oparin.solution.service.events;

import ru.oparin.solution.exception.WbRateLimitDeferException;

/**
 * Преобразование исключений из WB-вызовов в результат выполнения события.
 */
public final class WbEventExecutionErrors {

    private WbEventExecutionErrors() {
    }

    /**
     * Если в цепочке есть {@link WbRateLimitDeferException} — отложить событие; иначе retryable.
     */
    public static WbApiEventExecutionResult wrapDeferOrRetryable(Throwable e) {
        WbRateLimitDeferException defer = WbRateLimitDeferException.findInChain(e);
        if (defer != null) {
            return WbApiEventExecutionResult.deferredRateLimit(defer.getMessage(), defer.getDeferUntil());
        }
        return WbApiEventExecutionResult.retryableError(e.getMessage());
    }
}
