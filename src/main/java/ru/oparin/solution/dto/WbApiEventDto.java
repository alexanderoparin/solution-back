package ru.oparin.solution.dto;

import lombok.Builder;

import java.time.LocalDateTime;

@Builder
public record WbApiEventDto(
        Long id,
        String eventType,
        String status,
        String executorBeanName,
        Long cabinetId,
        String dedupKey,
        Integer attemptCount,
        Integer maxAttempts,
        LocalDateTime nextAttemptAt,
        String lastError,
        Integer priority,
        String triggerSource,
        LocalDateTime createdAt,
        LocalDateTime startedAt,
        LocalDateTime finishedAt,
        LocalDateTime updatedAt
) {
}
