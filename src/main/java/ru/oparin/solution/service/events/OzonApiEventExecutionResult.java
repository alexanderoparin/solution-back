package ru.oparin.solution.service.events;

import lombok.Builder;

import java.time.LocalDateTime;

@Builder
public record OzonApiEventExecutionResult(
        boolean success,
        boolean retryable,
        String errorMessage,
        LocalDateTime deferUntil,
        boolean countsAsAttempt
) {
    public static OzonApiEventExecutionResult completedSuccessfully() {
        return OzonApiEventExecutionResult.builder().success(true).countsAsAttempt(false).build();
    }

    public static OzonApiEventExecutionResult retryableError(String errorMessage) {
        return OzonApiEventExecutionResult.builder()
                .success(false)
                .retryable(true)
                .errorMessage(errorMessage)
                .countsAsAttempt(false)
                .build();
    }

    public static OzonApiEventExecutionResult finalError(String errorMessage) {
        return OzonApiEventExecutionResult.builder()
                .success(false)
                .retryable(false)
                .errorMessage(errorMessage)
                .countsAsAttempt(false)
                .build();
    }

    public static OzonApiEventExecutionResult deferredRetry(String message, LocalDateTime deferUntil) {
        return OzonApiEventExecutionResult.builder()
                .success(false)
                .retryable(true)
                .errorMessage(message)
                .deferUntil(deferUntil)
                .countsAsAttempt(true)
                .build();
    }
}
