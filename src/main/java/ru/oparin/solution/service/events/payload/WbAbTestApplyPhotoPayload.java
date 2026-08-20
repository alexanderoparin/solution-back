package ru.oparin.solution.service.events.payload;

import lombok.Builder;

/**
 * Payload асинхронной смены главного фото А/Б-теста (ротация или завершение).
 */
@Builder
public record WbAbTestApplyPhotoPayload(
        Long abTestId,
        Long variantId,
        String reason,
        /** Если true — после успешной смены фото тест переводится в DISABLED. */
        boolean finishAfterApply
) {
}
