package ru.oparin.solution.service.events;

import lombok.Builder;

import java.time.LocalDateTime;

@Builder
public record WbApiEventExecutionResult(
        boolean success,
        boolean retryable,
        boolean fallbackUsed,
        String errorMessage,
        LocalDateTime deferUntil
) {
    public static WbApiEventExecutionResult completedSuccessfully() {
        return WbApiEventExecutionResult.builder().success(true).build();
    }

    public static WbApiEventExecutionResult retryableError(String errorMessage) {
        return WbApiEventExecutionResult.builder()
                .success(false)
                .retryable(true)
                .errorMessage(errorMessage)
                .build();
    }

    public static WbApiEventExecutionResult finalError(String errorMessage) {
        return WbApiEventExecutionResult.builder()
                .success(false)
                .retryable(false)
                .errorMessage(errorMessage)
                .build();
    }

    public static WbApiEventExecutionResult fallbackSuccess(String message) {
        return WbApiEventExecutionResult.builder()
                .success(false)
                .fallbackUsed(true)
                .errorMessage(message)
                .build();
    }

    public static WbApiEventExecutionResult deferredRateLimit(String message, LocalDateTime deferUntil) {
        return WbApiEventExecutionResult.builder()
                .success(false)
                .retryable(true)
                .errorMessage(message)
                .deferUntil(deferUntil)
                .build();
    }
}
