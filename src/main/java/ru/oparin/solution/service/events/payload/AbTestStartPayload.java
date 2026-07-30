package ru.oparin.solution.service.events.payload;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Builder;

/**
 * Payload одного шага асинхронного старта А/Б-теста.
 * Старый формат (только {@code abTestId}) читается как шаг {@link AbTestStartStep#RESOLVE_CARD}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@Builder
public record AbTestStartPayload(
        Long abTestId,
        AbTestStartStep step,
        /** Для {@link AbTestStartStep#UPLOAD_VARIANT} — id варианта. */
        Long variantId
) {

    /**
     * Эффективный шаг с учётом обратной совместимости payload без {@code step}.
     */
    public AbTestStartStep resolvedStep() {
        return step != null ? step : AbTestStartStep.RESOLVE_CARD;
    }
}
