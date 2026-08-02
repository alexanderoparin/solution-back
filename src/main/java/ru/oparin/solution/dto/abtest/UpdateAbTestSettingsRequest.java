package ru.oparin.solution.dto.abtest;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import ru.oparin.solution.model.AbTestFinishAction;
import ru.oparin.solution.model.AbTestRotationMode;
import ru.oparin.solution.model.AbTestStopMode;

/**
 * Запрос на изменение настроек уже созданного А/Б-теста
 * (ротация, остановка, действие по завершении).
 */
@Data
public class UpdateAbTestSettingsRequest {

    @NotNull
    private AbTestRotationMode rotationMode;

    /** Порог показов для смены варианта (режим ROTATION_BY_VIEWS). */
    private Integer rotationViewsThreshold;

    /** Интервал ротации в минутах (режим ROTATION_BY_INTERVAL), 30–1440. */
    private Integer rotationIntervalMinutes;

    @NotNull
    private AbTestStopMode stopMode;

    /** Длительность теста в днях (режим BY_DURATION). */
    private Integer durationDays;

    @NotNull
    private AbTestFinishAction finishAction;
}
