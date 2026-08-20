package ru.oparin.solution.dto.abtest;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import ru.oparin.solution.model.WbAbTestFinishAction;
import ru.oparin.solution.model.WbAbTestRotationMode;
import ru.oparin.solution.model.WbAbTestStopMode;

/**
 * Запрос на изменение настроек уже созданного А/Б-теста
 * (ротация, остановка, действие по завершении).
 */
@Data
public class WbUpdateAbTestSettingsRequest {

    @NotNull
    private WbAbTestRotationMode rotationMode;

    /** Порог показов для смены варианта (режим ROTATION_BY_VIEWS). */
    private Integer rotationViewsThreshold;

    /** Интервал ротации в минутах (режим ROTATION_BY_INTERVAL), 30–1440. */
    private Integer rotationIntervalMinutes;

    @NotNull
    private WbAbTestStopMode stopMode;

    /** Длительность теста в днях (режим BY_DURATION). */
    private Integer durationDays;

    @NotNull
    private WbAbTestFinishAction finishAction;
}
