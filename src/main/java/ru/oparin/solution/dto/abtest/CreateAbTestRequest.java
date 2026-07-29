package ru.oparin.solution.dto.abtest;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import ru.oparin.solution.model.AbTestFinishAction;
import ru.oparin.solution.model.AbTestRotationMode;
import ru.oparin.solution.model.AbTestStopMode;

import java.util.List;

/**
 * Запрос на создание А/Б-теста (метаданные; файлы вариантов передаются отдельно).
 */
@Data
public class CreateAbTestRequest {

    @NotNull
    private Long nmId;

    @NotEmpty
    private List<Long> advertIds;

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
