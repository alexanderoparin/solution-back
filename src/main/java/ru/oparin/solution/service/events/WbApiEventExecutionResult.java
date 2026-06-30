package ru.oparin.solution.service.events;

import lombok.Builder;

import java.time.LocalDateTime;

@Builder
public record WbApiEventExecutionResult(
        boolean success,
        boolean retryable,
        boolean fallbackUsed,
        String errorMessage,
        LocalDateTime deferUntil,
        boolean countsAsAttempt
) {
    public static WbApiEventExecutionResult completedSuccessfully() {
        return WbApiEventExecutionResult.builder().success(true).countsAsAttempt(false).build();
    }

    public static WbApiEventExecutionResult retryableError(String errorMessage) {
        return WbApiEventExecutionResult.builder()
                .success(false)
                .retryable(true)
                .errorMessage(errorMessage)
                .countsAsAttempt(false)
                .build();
    }

    public static WbApiEventExecutionResult finalError(String errorMessage) {
        return WbApiEventExecutionResult.builder()
                .success(false)
                .retryable(false)
                .errorMessage(errorMessage)
                .countsAsAttempt(false)
                .build();
    }

    public static WbApiEventExecutionResult fallbackSuccess(String message) {
        return WbApiEventExecutionResult.builder()
                .success(false)
                .fallbackUsed(true)
                .errorMessage(message)
                .countsAsAttempt(false)
                .build();
    }

    /**
     * Отложенный повтор после неуспешного вызова WB (таймаут, 504, 429 и т.п.) — увеличивает {@code attempt_count}.
     */
    public static WbApiEventExecutionResult deferredRateLimit(String message, LocalDateTime deferUntil) {
        return WbApiEventExecutionResult.builder()
                .success(false)
                .retryable(true)
                .errorMessage(message)
                .deferUntil(deferUntil)
                .countsAsAttempt(true)
                .build();
    }

    /**
     * Отложенный повтор только из‑за слота лимитера (ещё не было HTTP) — {@code attempt_count} не меняется.
     */
    public static WbApiEventExecutionResult deferredScheduling(String message, LocalDateTime deferUntil) {
        return WbApiEventExecutionResult.builder()
                .success(false)
                .retryable(true)
                .errorMessage(message)
                .deferUntil(deferUntil)
                .countsAsAttempt(false)
                .build();
    }
}
