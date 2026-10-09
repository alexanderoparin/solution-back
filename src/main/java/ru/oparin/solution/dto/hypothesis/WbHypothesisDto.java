package ru.oparin.solution.dto.hypothesis;

import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Гипотеза для списка и карточки.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WbHypothesisDto {
    private Long id;
    private Long cabinetId;
    private Long nmId;
    private String nmName;
    private String nmPhotoUrl;
    private String vendorCode;
    private String categoryName;
    private String title;
    private String description;
    private LocalDate checkFrom;
    private LocalDate checkTo;
    private LocalDate baselineFrom;
    private LocalDate baselineTo;
    private String workflowStatus;
    private String displayStatus;
    private String verdict;
    private boolean verdictManual;
    private List<String> criteria;
    private List<WbHypothesisCriterionValueDto> criterionResults;
    private List<WbHypothesisDailyPointDto> dynamics;
    private String resultSummary;
    private Long createdByUserId;
    private String createdByName;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
