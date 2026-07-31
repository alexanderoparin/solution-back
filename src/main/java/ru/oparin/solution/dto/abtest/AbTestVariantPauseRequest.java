package ru.oparin.solution.dto.abtest;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * Запрос постановки варианта А/Б-теста на паузу / снятия с паузы.
 */
@Data
public class AbTestVariantPauseRequest {

    @NotNull
    private Boolean paused;
}
