package ru.oparin.solution.dto.abtest;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import ru.oparin.solution.model.WbAbTestFinishAction;
import ru.oparin.solution.model.WbAbTestRotationMode;
import ru.oparin.solution.model.WbAbTestStopMode;

import java.util.List;

/**
 * Запрос на создание А/Б-теста (метаданные; файлы вариантов передаются отдельно).
 */
@Data
public class WbCreateAbTestRequest {

    @NotNull
    private Long nmId;

    @NotEmpty
    private List<Long> advertIds;

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
