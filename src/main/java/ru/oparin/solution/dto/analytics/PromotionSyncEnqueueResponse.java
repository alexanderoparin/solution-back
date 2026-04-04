package ru.oparin.solution.dto.analytics;

/**
 * Ответ на запрос постановки в очередь синхронизации рекламы (кампании + статистика).
 */
public record PromotionSyncEnqueueResponse(boolean enqueued) {
}
