package ru.oparin.solution.service.events.payload;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Builder;

/**
 * Payload одного шага асинхронного старта А/Б-теста.
 * Старый формат (только {@code abTestId}) читается как шаг {@link WbAbTestStartStep#RESOLVE_CARD}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@Builder
public record WbAbTestStartPayload(
        Long abTestId,
        WbAbTestStartStep step,
        /** Для {@link WbAbTestStartStep#UPLOAD_VARIANT} — id варианта. */
        Long variantId
) {

    /**
     * Эффективный шаг с учётом обратной совместимости payload без {@code step}.
     */
    public WbAbTestStartStep resolvedStep() {
        return step != null ? step : WbAbTestStartStep.RESOLVE_CARD;
    }
}
