package ru.oparin.solution.dto.analytics;

/**
 * Ответ на постановку в очередь запуска или паузы рекламной кампании.
 */
public record CampaignControlEnqueueResponse(
        boolean enqueued,
        Long eventId,
        String message
) {
}
