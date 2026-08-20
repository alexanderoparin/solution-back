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
public class WbAbTestDto {

    private Long id;
    private Long cabinetId;
    private Long nmId;
    private String title;
    private WbAbTestStatus status;
    private WbAbTestRotationMode rotationMode;
    private Integer rotationViewsThreshold;
    private Integer rotationIntervalMinutes;
    private WbAbTestStopMode stopMode;
    private Integer durationDays;
    private LocalDateTime endsAt;
    private WbAbTestFinishAction finishAction;
    private Long activeVariantId;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private WbAbTestInsightCode insightCode;
    private String insightLabel;
    private String lastWbError;
    /** Можно перезапустить (упал/отменён на старте, не дошёл до ENABLED). */
    private Boolean canRestart;
    private List<Long> advertIds;
    private List<WbAbTestVariantDto> variants;
}
