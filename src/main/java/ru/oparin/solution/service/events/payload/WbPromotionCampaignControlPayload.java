package ru.oparin.solution.service.events.payload;

import lombok.Builder;

/**
 * Payload события управления рекламной кампанией (запуск / пауза).
 */
@Builder
public record WbPromotionCampaignControlPayload(
        Long advertId
) {
}
