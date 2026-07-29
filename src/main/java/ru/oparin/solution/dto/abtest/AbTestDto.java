package ru.oparin.solution.dto.abtest;

import lombok.Builder;
import lombok.Data;
import ru.oparin.solution.model.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Карточка А/Б-теста для списка и деталки.
 */
@Data
@Builder
public class AbTestDto {

    private Long id;
    private Long cabinetId;
    private Long nmId;
    private String title;
    private AbTestStatus status;
    private AbTestRotationMode rotationMode;
    private Integer rotationViewsThreshold;
    private Integer rotationIntervalMinutes;
    private AbTestStopMode stopMode;
    private Integer durationDays;
    private LocalDateTime endsAt;
    private AbTestFinishAction finishAction;
    private Long activeVariantId;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private AbTestInsightCode insightCode;
    private String insightLabel;
    private String lastWbError;
    private List<Long> advertIds;
    private List<AbTestVariantDto> variants;
}
